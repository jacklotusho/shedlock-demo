package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread Type 2 — Install Software
 * Simulates software installation, then triggers Type 3 (Configure Network).
 */
@Slf4j
public class InstallSoftwareThread extends BaseProvisionThread {

    public InstallSoftwareThread(Task task, TaskService taskService) {
        super(task, taskService);
    }

    @Override
    protected void execute() throws Exception {
        log.info("[{}] Installing software for uuid={}", workerName, task.getUuid());

        // Simulate package installation
        Thread.sleep(2000);
        log.info("[{}] Software installed for uuid={}", workerName, task.getUuid());

        // Chain: trigger next step — Configure Network (type=3, sequence=3)
        triggerNextTask(3, 3, "Configure network for " + task.getUuid());
    }
}
