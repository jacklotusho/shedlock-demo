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
 * Picks up PENDING tasks every 5s and dispatches to the worker thread pool.
 *
 * ⚠️  NOT @Transactional — intentionally.
 *     A single transaction wrapping the whole loop would hold a DB connection
 *     for the entire batch dispatch duration, and claimTask's @Modifying
 *     flushes would fight with Hibernate's 1st-level cache.
 *     Each service call (fetchPendingTasks, claimTask, failTask) manages
 *     its own short transaction instead.
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

        // Each call is its own @Transactional — no outer transaction here
        List<Task> pending = taskService.fetchPendingTasks(batchSize);
        if (pending.isEmpty()) {
            log.debug("[{}] No pending tasks", podName);
            return;
        }

        log.info("[{}] Found {} pending task(s) — dispatching", podName, pending.size());

        for (Task task : pending) {
            // Own @Transactional — atomic UPDATE WHERE state=PENDING
            boolean claimed = taskService.claimTask(task.getId(), podName);
            if (!claimed) {
                log.debug("[{}] Task {} already claimed — skipping", podName, task.getId());
                continue;
            }

            try {
                BaseProvisionThread worker = threadFactory.create(task);
                executorService.submit(worker);
                log.info("[{}] Task {} (type={}) dispatched to thread pool", podName, task.getId(), task.getType());
            } catch (IllegalArgumentException e) {
                log.error("[{}] Unknown task type {}: {}", podName, task.getType(), e.getMessage());
                taskService.failTask(task.getId(), "Unknown task type: " + task.getType());
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