package com.demo.shedlock.service;

import com.demo.shedlock.entity.Task;

import java.util.List;

public interface TaskService {

    /**
     * Save task only if no task with same (uuid, sequence, type) exists.
     * Uses INSERT IGNORE pattern — safe under concurrent execution.
     */
    Task saveIfNotExists(Task task);

    /**
     * Atomically claim a PENDING task for processing.
     * Returns true only if this thread won the race.
     */
    boolean claimTask(Long taskId, String processedBy);

    /** Mark task COMPLETED */
    void completeTask(Long taskId);

    /** Mark task FAILED with error message */
    void failTask(Long taskId, String errorMessage);

    /** Fetch pending tasks up to batchSize */
    List<Task> fetchPendingTasks(int batchSize);

    /** Create the next task in a workflow sequence */
    Task triggerNextTask(Task nextTask);

    List<Task> findByUuid(String uuid);
}
