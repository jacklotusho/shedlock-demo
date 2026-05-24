package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.extern.slf4j.Slf4j;

/**
 * Thread Type 1 — Provision VM
 * Simulates VM provisioning, then triggers Type 2 (Install Software).
 */
@Slf4j
public class ProvisionVmThread extends BaseProvisionThread {

    public ProvisionVmThread(Task task, TaskService taskService) {
        super(task, taskService);
    }

    @Override
    protected void execute() throws Exception {
        log.info("[{}] Provisioning VM for uuid={}", workerName, task.getUuid());

        // Simulate VM provisioning work
        Thread.sleep(1500);
        log.info("[{}] VM provisioned — IP assigned for uuid={}", workerName, task.getUuid());

        // Chain: trigger next step — Install Software (type=2, sequence=2)
        triggerNextTask(2, 2, "Install software on " + task.getUuid());
    }
}
