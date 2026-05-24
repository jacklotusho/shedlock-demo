package com.demo.shedlock.service;

import com.demo.shedlock.entity.Task;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface TaskService {

    /** Save only if no task with same (uuid, sequence, type) exists */
    Mono<Task> saveIfNotExists(Task task);

    /** Atomically claim a PENDING task — returns true if this caller won */
    Mono<Boolean> claimTask(Long taskId, String processedBy);

    /** Mark task COMPLETED */
    Mono<Void> completeTask(Long taskId);

    /** Mark task FAILED */
    Mono<Void> failTask(Long taskId, String errorMessage);

    /** Fetch up to batchSize PENDING tasks */
    Flux<Task> fetchPendingTasks(int batchSize);

    /** Chain the next workflow task — duplicate-safe */
    Mono<Task> triggerNextTask(Task nextTask);

    Flux<Task> findByUuid(String uuid);
}
