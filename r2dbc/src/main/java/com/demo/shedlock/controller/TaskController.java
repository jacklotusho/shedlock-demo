package com.demo.shedlock.controller;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.entity.Task.TaskState;
import com.demo.shedlock.repository.TaskRepository;
import com.demo.shedlock.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.UUID;

/**
 * Reactive REST controller — all handlers return Mono/Flux.
 * Spring WebFlux dispatches these without blocking any thread.
 */
@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService    taskService;
    private final TaskRepository taskRepository;

    /**
     * POST /api/requests
     * Creates a new workflow — seeds the first task (type=1 Provision VM).
     * TaskPickupScheduler picks it up within 5s.
     */
    @PostMapping("/requests")
    public Mono<ResponseEntity<Map<String, Object>>> createRequest(
            @RequestBody(required = false) Map<String, String> body) {

        String uuid  = UUID.randomUUID().toString();
        String label = (body != null && body.containsKey("label"))
            ? body.get("label")
            : "Request-" + uuid.substring(0, 8);

        Task initial = new Task(uuid, 1, 1, "Provision VM for " + label);

        return taskService.saveIfNotExists(initial)
            .doOnNext(saved -> log.info("Workflow created: uuid={} label={} taskId={}",
                uuid, label, saved.getId()))
            .map(saved -> ResponseEntity.ok(Map.<String, Object>of(
                "uuid",    uuid,
                "label",   label,
                "taskId",  saved.getId(),
                "message", "Workflow started. Poll /api/requests/" + uuid
            )));
    }

    /**
     * GET /api/requests/{uuid}
     * Returns all tasks for a workflow, sorted by sequence.
     */
    @GetMapping("/requests/{uuid}")
    public Mono<ResponseEntity<Map<String, Object>>> getRequest(@PathVariable String uuid) {
        return taskService.findByUuid(uuid)
            .sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()))
            .collectList()
            .map(tasks -> {
                if (tasks.isEmpty()) {
                    return ResponseEntity.<Map<String, Object>>notFound().build();
                }
                boolean complete = tasks.stream()
                    .anyMatch(t -> t.getType() == 4
                        && TaskState.COMPLETED.name().equals(t.getState()));
                boolean failed = tasks.stream()
                    .anyMatch(t -> TaskState.FAILED.name().equals(t.getState()));
                String status = failed ? "FAILED" : complete ? "COMPLETED" : "IN_PROGRESS";

                return ResponseEntity.ok(Map.<String, Object>of(
                    "uuid",   uuid,
                    "status", status,
                    "steps",  tasks.size(),
                    "tasks",  tasks
                ));
            });
    }

    /**
     * GET /api/tasks/stats
     * Returns count per state.
     */
    @GetMapping("/tasks/stats")
    public Mono<Map<String, Object>> getStats() {
        return Mono.zip(
            taskRepository.countByState(TaskState.PENDING.name()),
            taskRepository.countByState(TaskState.RUNNING.name()),
            taskRepository.countByState(TaskState.COMPLETED.name()),
            taskRepository.countByState(TaskState.FAILED.name())
        ).map(t -> Map.of(
            "pending",   t.getT1(),
            "running",   t.getT2(),
            "completed", t.getT3(),
            "failed",    t.getT4()
        ));
    }
}
