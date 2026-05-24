package com.demo.shedlock;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.entity.Task.TaskState;
import com.demo.shedlock.repository.TaskRepository;
import com.demo.shedlock.service.TaskServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.time.Instant;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock
    TaskRepository taskRepository;

    @InjectMocks
    TaskServiceImpl taskService;

    Task task;

    @BeforeEach
    void setup() {
        task = new Task("uuid-123", 1, 1, "Provision VM");
        task.setId(1L);
    }

    @Test
    @DisplayName("saveIfNotExists — saves new task when none exists")
    void saveIfNotExists_newTask() {
        when(taskRepository.findByUuidAndSequenceAndType("uuid-123", 1, 1))
            .thenReturn(Optional.empty());
        when(taskRepository.save(any())).thenReturn(task);

        Task result = taskService.saveIfNotExists(task);

        assertThat(result.getId()).isEqualTo(1L);
        verify(taskRepository, times(1)).save(task);
    }

    @Test
    @DisplayName("saveIfNotExists — returns existing task without inserting")
    void saveIfNotExists_duplicate() {
        when(taskRepository.findByUuidAndSequenceAndType("uuid-123", 1, 1))
            .thenReturn(Optional.of(task));

        Task result = taskService.saveIfNotExists(task);

        assertThat(result.getId()).isEqualTo(1L);
        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveIfNotExists — handles race condition (DataIntegrityViolationException)")
    void saveIfNotExists_raceCondition() {
        when(taskRepository.findByUuidAndSequenceAndType("uuid-123", 1, 1))
            .thenReturn(Optional.empty())         // first call: not found
            .thenReturn(Optional.of(task));       // second call after exception: found
        when(taskRepository.save(any()))
            .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

        Task result = taskService.saveIfNotExists(task);

        assertThat(result.getId()).isEqualTo(1L);
        verify(taskRepository, times(1)).save(any()); // attempted once, then fell back
    }

    @Test
    @DisplayName("claimTask — returns true when UPDATE affects 1 row")
    void claimTask_success() {
        when(taskRepository.claimTask(eq(1L), eq("pod-1"), any(Instant.class))).thenReturn(1);

        boolean claimed = taskService.claimTask(1L, "pod-1");

        assertThat(claimed).isTrue();
    }

    @Test
    @DisplayName("claimTask — returns false when task already claimed by another worker")
    void claimTask_alreadyClaimed() {
        when(taskRepository.claimTask(eq(1L), eq("pod-2"), any(Instant.class))).thenReturn(0);

        boolean claimed = taskService.claimTask(1L, "pod-2");

        assertThat(claimed).isFalse();
    }

    @Test
    @DisplayName("completeTask — sets state to COMPLETED")
    void completeTask() {
        task.setState(TaskState.RUNNING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        taskService.completeTask(1L);

        assertThat(task.getState()).isEqualTo(TaskState.COMPLETED);
        assertThat(task.getCompletedAt()).isNotNull();
    }

    @Test
    @DisplayName("failTask — sets state to FAILED with error message")
    void failTask() {
        task.setState(TaskState.RUNNING);
        when(taskRepository.findById(1L)).thenReturn(Optional.of(task));
        when(taskRepository.save(any())).thenReturn(task);

        taskService.failTask(1L, "Connection timeout");

        assertThat(task.getState()).isEqualTo(TaskState.FAILED);
        assertThat(task.getErrorMessage()).isEqualTo("Connection timeout");
        assertThat(task.getRetryCount()).isEqualTo(1);
    }
}
