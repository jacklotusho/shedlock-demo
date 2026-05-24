package com.demo.shedlock.scheduler;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import com.demo.shedlock.thread.BaseProvisionThread;
import com.demo.shedlock.thread.ProvisionThreadFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.util.List;
import java.util.concurrent.ExecutorService;

/**
 * Polls for PENDING tasks every 5s and dispatches to the worker thread pool.
 *
 * @Scheduled is blocking — intentional. This is the boundary between the
 * reactive world (R2DBC) and the blocking world (provision threads).
 *
 * ShedLock R2DBC provider internally uses reactor to acquire the lock,
 * then blocks the scheduler thread until lock is held.
 * Only ONE pod runs this method at a time across the cluster.
 *
 * .collectList().block() fetches the reactive stream into a List on this
 * thread — safe because @Scheduled runs on a dedicated scheduler thread,
 * not on the R2DBC event loop.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TaskPickupScheduler {

    private final TaskService            taskService;
    private final ProvisionThreadFactory threadFactory;
    private final ExecutorService        executorService;

    @Value("${task.scheduler.batch-size:10}")
    private int batchSize;

    @Scheduled(fixedDelay = 5000, initialDelay = 2000)
    @SchedulerLock(
        name           = "TaskScheduler_checkTasks",
        lockAtLeastFor = "4s",
        lockAtMostFor  = "4500ms"
    )
    public void checkTasks() {
        String podName = getPodName();

        // Bridge reactive → blocking: safe on scheduler thread
        List<Task> pending = taskService.fetchPendingTasks(batchSize)
            .collectList()
            .block();

        if (pending == null || pending.isEmpty()) {
            log.debug("[{}] No pending tasks", podName);
            return;
        }

        log.info("[{}] Found {} pending task(s) — dispatching", podName, pending.size());

        for (Task task : pending) {
            // Atomic claim — reactive bridge
            Boolean claimed = taskService.claimTask(task.getId(), podName).block();
            if (!Boolean.TRUE.equals(claimed)) {
                log.debug("[{}] Task {} already claimed — skipping", podName, task.getId());
                continue;
            }

            try {
                BaseProvisionThread worker = threadFactory.create(task);
                executorService.submit(worker);
                log.info("[{}] Task {} (type={}) dispatched", podName, task.getId(), task.getType());
            } catch (IllegalArgumentException e) {
                log.error("[{}] Unknown task type {}: {}", podName, task.getType(), e.getMessage());
                taskService.failTask(task.getId(), "Unknown task type: " + task.getType()).block();
            }
        }
    }

    private String getPodName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown-pod";
        }
    }
}
