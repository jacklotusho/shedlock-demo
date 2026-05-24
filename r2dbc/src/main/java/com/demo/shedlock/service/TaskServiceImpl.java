package com.demo.shedlock.service;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.entity.Task.TaskState;
import com.demo.shedlock.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    /**
     * Mono.defer() makes save() LAZY — only called when upstream is empty.
     * Without defer, save() fires eagerly as a Java method argument
     * regardless of whether findByUuidAndSequenceAndType found a result.
     */
    @Override
    public Mono<Task> saveIfNotExists(Task task) {
        return taskRepository
            .findByUuidAndSequenceAndType(task.getUuid(), task.getSequence(), task.getType())
            .switchIfEmpty(Mono.defer(() ->
                taskRepository.save(task)
                    .doOnSuccess(saved -> log.debug(
                        "Task created: uuid={} seq={} type={}",
                        saved.getUuid(), saved.getSequence(), saved.getType()))
                    .onErrorResume(DataIntegrityViolationException.class, ex -> {
                        log.warn("Duplicate prevented (race): uuid={} seq={} type={}",
                            task.getUuid(), task.getSequence(), task.getType());
                        return taskRepository.findByUuidAndSequenceAndType(
                            task.getUuid(), task.getSequence(), task.getType());
                    })
            ));
    }

    @Override
    public Mono<Boolean> claimTask(Long taskId, String processedBy) {
        return taskRepository.claimTask(taskId, processedBy, LocalDateTime.now())
            .map(rows -> rows == 1)
            .doOnNext(claimed -> {
                if (claimed) log.debug("Task {} claimed by {}", taskId, processedBy);
            });
    }

    @Override
    public Mono<Void> completeTask(Long taskId) {
        return taskRepository.completeTask(taskId, LocalDateTime.now())
            .doOnNext(rows -> log.info("Task {} COMPLETED (rows={})", taskId, rows))
            .then();
    }

    @Override
    public Mono<Void> failTask(Long taskId, String errorMessage) {
        return taskRepository.failTask(taskId, LocalDateTime.now(), errorMessage)
            .doOnNext(rows -> log.warn("Task {} FAILED: {}", taskId, errorMessage))
            .then();
    }

    @Override
    public Flux<Task> fetchPendingTasks(int batchSize) {
        return taskRepository
            .findByStateOrderByCreatedAtAsc(TaskState.PENDING.name())
            .take(batchSize);
    }

    @Override
    public Mono<Task> triggerNextTask(Task nextTask) {
        return saveIfNotExists(nextTask)
            .doOnNext(saved -> log.info("Next task triggered: uuid={} seq={} type={}",
                saved.getUuid(), saved.getSequence(), saved.getType()));
    }

    @Override
    public Flux<Task> findByUuid(String uuid) {
        return taskRepository.findByUuid(uuid);
    }
}
