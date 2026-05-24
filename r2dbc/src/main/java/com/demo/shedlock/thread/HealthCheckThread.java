package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread Type 4 — Health Check (terminal step)
 * Final workflow step — no triggerNextTask() call.
 */
@Slf4j
public class HealthCheckThread extends BaseProvisionThread {

    public HealthCheckThread(Task task, TaskService taskService) {
        super(task, taskService);
    }

    @Override
    protected void execute() throws Exception {
        log.info("[{}] Running health check for uuid={}", workerName, task.getUuid());

        // Simulate health probe
        Thread.sleep(500);
        log.info("[{}] ✅ Health check PASSED — workflow complete for uuid={}", workerName, task.getUuid());

        // Terminal step — no triggerNextTask()
    }
}
