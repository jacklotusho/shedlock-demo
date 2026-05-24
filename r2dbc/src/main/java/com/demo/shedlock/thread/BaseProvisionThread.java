package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for all blocking provision worker threads.
 *
 * Design: provision work is inherently blocking (SSH calls, HTTP polls, sleeps).
 * We keep threads blocking and bridge to the reactive TaskService via .block().
 *
 * .block() is safe here because:
 *  - These threads run on a dedicated bounded thread pool (task-worker-N), NOT
 *    on the R2DBC event loop (netty NIO threads).
 *  - Blocking on a non-reactor thread never starves the R2DBC connection pool.
 */
@Slf4j
public abstract class BaseProvisionThread implements Runnable {

    protected final Task        task;
    protected final TaskService taskService;
    protected       String      workerName;

    protected BaseProvisionThread(Task task, TaskService taskService) {
        this.task        = task;
        this.taskService = taskService;
    }

    @Override
    public final void run() {
        this.workerName = Thread.currentThread().getName();
        log.info("[{}] Starting  id={} uuid={} type={} desc={}",
            workerName, task.getId(), task.getUuid(), task.getType(), task.getDescription());
        try {
            execute();
            // Bridge: block() is safe on task-worker thread (not reactor event loop)
            taskService.completeTask(task.getId()).block();
            log.info("[{}] Completed id={}", workerName, task.getId());
        } catch (Exception e) {
            log.error("[{}] Failed    id={} error={}", workerName, task.getId(), e.getMessage(), e);
            taskService.failTask(task.getId(), e.getMessage()).block();
        }
    }

    protected abstract void execute() throws Exception;

    /**
     * Chain the next workflow step — reactive bridge via .block().
     * Safe to call from worker thread.
     */
    protected Task triggerNextTask(int nextSequence, int nextType, String description) {
        Task next  = new Task(task.getUuid(), nextSequence, nextType, description);
        Task saved = taskService.triggerNextTask(next).block();
        log.debug("[{}] Triggered next: seq={} type={}", workerName, nextSequence, nextType);
        return saved;
    }
}
