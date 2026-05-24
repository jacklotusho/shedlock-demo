package com.demo.shedlock.thread;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Factory that maps Task.type → the correct BaseProvisionThread subclass.
 *
 * Adding a new task type:
 *  1. Create YourNewThread extends BaseProvisionThread
 *  2. Add a case here
 *  3. Done — TaskScheduler picks it up automatically
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProvisionThreadFactory {

    private final TaskService taskService;

    public BaseProvisionThread create(Task task) {
        return switch (task.getType()) {
            case 1 -> new ProvisionVmThread(task, taskService);
            case 2 -> new InstallSoftwareThread(task, taskService);
            case 3 -> new ConfigureNetworkThread(task, taskService);
            case 4 -> new HealthCheckThread(task, taskService);
            default -> throw new IllegalArgumentException(
                "Unknown task type: " + task.getType() + " for task id=" + task.getId());
        };
    }
}
