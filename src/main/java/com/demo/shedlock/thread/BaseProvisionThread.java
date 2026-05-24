package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.extern.slf4j.Slf4j;

/**
 * Base class for all provision worker threads.
 *
 * Key design notes:
 * - workerName is captured in run(), NOT in the constructor.
 *   The constructor runs on the scheduler thread; run() runs on the worker thread.
 * - taskService calls (completeTask, failTask, triggerNextTask) are Spring-managed
 *   beans injected via the factory — @Transactional works correctly because the
 *   calls go through Spring's proxy, regardless of which thread invokes them.
 */
@Slf4j
public abstract class BaseProvisionThread implements Runnable {

    protected final Task        task;
    protected final TaskService taskService;
    protected       String      workerName;   // set in run(), not constructor

    protected BaseProvisionThread(Task task, TaskService taskService) {
        this.task        = task;
        this.taskService = taskService;
    }

    @Override
    public final void run() {
        // Capture the actual worker thread name here, not at construction time
        this.workerName = Thread.currentThread().getName();

        log.info("[{}] Starting  id={} uuid={} type={} desc={}",
            workerName, task.getId(), task.getUuid(), task.getType(), task.getDescription());
        try {
            execute();
            taskService.completeTask(task.getId());
            log.info("[{}] Completed id={}", workerName, task.getId());
        } catch (Exception e) {
            log.error("[{}] Failed    id={} error={}", workerName, task.getId(), e.getMessage(), e);
            taskService.failTask(task.getId(), e.getMessage());
        }
    }

    protected abstract void execute() throws Exception;

    /**
     * Chain the next workflow step — duplicate-safe via saveIfNotExists.
     * Call this AFTER the current step's work succeeds, before returning from execute().
     */
    protected Task triggerNextTask(int nextSequence, int nextType, String description) {
        Task next = new Task(task.getUuid(), nextSequence, nextType, description);
        Task saved = taskService.triggerNextTask(next);
        log.debug("[{}] Triggered next task seq={} type={}", workerName, nextSequence, nextType);
        return saved;
    }
}
