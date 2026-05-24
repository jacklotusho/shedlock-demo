package com.demo.shedlock.service;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.entity.Task.TaskState;
import com.demo.shedlock.repository.TaskRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskServiceImpl implements TaskService {

    private final TaskRepository taskRepository;

    /**
     * Check-then-insert with DataIntegrityViolationException fallback.
     * The DB unique constraint (uuid, sequence, type) is the final authority.
     * Even under concurrent access, at most one row is created.
     */
    @Override
    @Transactional
    public Task saveIfNotExists(Task task) {
        return taskRepository
            .findByUuidAndSequenceAndType(task.getUuid(), task.getSequence(), task.getType())
            .orElseGet(() -> {
                try {
                    Task saved = taskRepository.save(task);
                    log.debug("Task created: uuid={} seq={} type={} desc={}",
                        task.getUuid(), task.getSequence(), task.getType(), task.getDescription());
                    return saved;
                } catch (DataIntegrityViolationException e) {
                    // Race condition: another thread created it between our check and save
                    log.warn("Duplicate task prevented (race): uuid={} seq={} type={}",
                        task.getUuid(), task.getSequence(), task.getType());
                    return taskRepository
                        .findByUuidAndSequenceAndType(task.getUuid(), task.getSequence(), task.getType())
                        .orElseThrow(() -> new IllegalStateException(
                            "Task disappeared after duplicate violation — this should never happen"));
                }
            });
    }

    /**
     * Atomic optimistic claim via UPDATE WHERE state = PENDING.
     * Returns true only if this thread successfully claimed the row.
     * All other threads calling claimTask for the same id get false.
     */
    @Override
    @Transactional
    public boolean claimTask(Long taskId, String processedBy) {
        int updated = taskRepository.claimTask(taskId, processedBy, Instant.now());
        if (updated == 1) {
            log.debug("Task {} claimed by {}", taskId, processedBy);
        }
        return updated == 1;
    }

    @Override
    @Transactional
    public void completeTask(Long taskId) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setState(TaskState.COMPLETED);
            task.setCompletedAt(Instant.now());
            taskRepository.save(task);
            log.info("Task {} COMPLETED", taskId);
        });
    }

    @Override
    @Transactional
    public void failTask(Long taskId, String errorMessage) {
        taskRepository.findById(taskId).ifPresent(task -> {
            task.setState(TaskState.FAILED);
            task.setErrorMessage(errorMessage);
            task.setCompletedAt(Instant.now());
            task.setRetryCount(task.getRetryCount() + 1);
            taskRepository.save(task);
            log.warn("Task {} FAILED: {}", taskId, errorMessage);
        });
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> fetchPendingTasks(int batchSize) {
        return taskRepository.findByStateOrderByCreatedAt(
            TaskState.PENDING,
            PageRequest.of(0, batchSize)
        );
    }

    @Override
    @Transactional
    public Task triggerNextTask(Task nextTask) {
        Task saved = saveIfNotExists(nextTask);
        log.info("Next task triggered: uuid={} seq={} type={}",
            nextTask.getUuid(), nextTask.getSequence(), nextTask.getType());
        return saved;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Task> findByUuid(String uuid) {
        return taskRepository.findByUuid(uuid);
    }
}
