package com.demo.shedlock;

import com.demo.shedlock.entity.Task;
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
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TaskServiceTest {

    @Mock  TaskRepository  taskRepository;
    @InjectMocks TaskServiceImpl taskService;

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
            .thenReturn(Mono.empty());
        when(taskRepository.save(any())).thenReturn(Mono.just(task));

        StepVerifier.create(taskService.saveIfNotExists(task))
            .expectNextMatches(t -> t.getId().equals(1L))
            .verifyComplete();

        verify(taskRepository).save(task);
    }

    @Test
    @DisplayName("saveIfNotExists — returns existing task, never calls save()")
    void saveIfNotExists_duplicate() {
        when(taskRepository.findByUuidAndSequenceAndType("uuid-123", 1, 1))
            .thenReturn(Mono.just(task));
        // No save() stub — Mono.defer() ensures save() is never called
        // Mockito strict mode would fail if save() were stubbed but not invoked

        StepVerifier.create(taskService.saveIfNotExists(task))
            .expectNextMatches(t -> t.getId().equals(1L))
            .verifyComplete();

        verify(taskRepository, never()).save(any());
    }

    @Test
    @DisplayName("saveIfNotExists — handles race condition (DataIntegrityViolationException)")
    void saveIfNotExists_raceCondition() {
        when(taskRepository.findByUuidAndSequenceAndType("uuid-123", 1, 1))
            .thenReturn(Mono.empty())       // 1st call: not found
            .thenReturn(Mono.just(task));   // 2nd call after race: found
        when(taskRepository.save(any()))
            .thenReturn(Mono.error(new DataIntegrityViolationException("Duplicate")));

        StepVerifier.create(taskService.saveIfNotExists(task))
            .expectNextMatches(t -> t.getId().equals(1L))
            .verifyComplete();
    }

    @Test
    @DisplayName("claimTask — returns true when 1 row updated")
    void claimTask_success() {
        when(taskRepository.claimTask(eq(1L), eq("pod-1"), any(LocalDateTime.class)))
            .thenReturn(Mono.just(1));

        StepVerifier.create(taskService.claimTask(1L, "pod-1"))
            .expectNext(true)
            .verifyComplete();
    }

    @Test
    @DisplayName("claimTask — returns false when task already claimed")
    void claimTask_alreadyClaimed() {
        when(taskRepository.claimTask(eq(1L), eq("pod-2"), any(LocalDateTime.class)))
            .thenReturn(Mono.just(0));

        StepVerifier.create(taskService.claimTask(1L, "pod-2"))
            .expectNext(false)
            .verifyComplete();
    }

    @Test
    @DisplayName("completeTask — delegates to repository")
    void completeTask() {
        when(taskRepository.completeTask(eq(1L), any(LocalDateTime.class)))
            .thenReturn(Mono.just(1));

        StepVerifier.create(taskService.completeTask(1L))
            .verifyComplete();

        verify(taskRepository).completeTask(eq(1L), any(LocalDateTime.class));
    }

    @Test
    @DisplayName("failTask — delegates with error message")
    void failTask() {
        when(taskRepository.failTask(eq(1L), any(LocalDateTime.class), eq("timeout")))
            .thenReturn(Mono.just(1));

        StepVerifier.create(taskService.failTask(1L, "timeout"))
            .verifyComplete();

        verify(taskRepository).failTask(eq(1L), any(LocalDateTime.class), eq("timeout"));
    }
}
