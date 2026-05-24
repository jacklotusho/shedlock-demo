package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread Type 3 — Configure Network
 * Simulates network config, then triggers Type 4 (Health Check).
 */
@Slf4j
public class ConfigureNetworkThread extends BaseProvisionThread {

    public ConfigureNetworkThread(Task task, TaskService taskService) {
        super(task, taskService);
    }

    @Override
    protected void execute() throws Exception {
        log.info("[{}] Configuring network for uuid={}", workerName, task.getUuid());

        // Simulate network configuration
        Thread.sleep(1000);
        log.info("[{}] Network configured for uuid={}", workerName, task.getUuid());

        // Chain: trigger final step — Health Check (type=4, sequence=4)
        triggerNextTask(4, 4, "Health check for " + task.getUuid());
    }
}
