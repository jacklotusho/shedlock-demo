package com.demo.shedlock.controller;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.entity.Task.TaskState;
import com.demo.shedlock.repository.TaskRepository;
import com.demo.shedlock.service.TaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService    taskService;
    private final TaskRepository taskRepository;

    /** POST /api/requests — create new workflow, seeds first task */
    @PostMapping("/requests")
    public ResponseEntity<Map<String, Object>> createRequest(
            @RequestBody(required = false) Map<String, String> body) {

        String uuid  = UUID.randomUUID().toString();
        String label = (body != null && body.containsKey("label"))
            ? body.get("label")
            : "Request-" + uuid.substring(0, 8);

        Task initial = new Task(uuid, 1, 1, "Provision VM for " + label);
        Task saved   = taskService.saveIfNotExists(initial);

        log.info("Workflow created: uuid={} label={} taskId={}", uuid, label, saved.getId());

        return ResponseEntity.ok(Map.of(
            "uuid",    uuid,
            "label",   label,
            "taskId",  saved.getId(),
            "message", "Poll /api/requests/" + uuid + " to track progress."
        ));
    }

    /** GET /api/requests/{uuid} — get all tasks for a workflow */
    @GetMapping("/requests/{uuid}")
    public ResponseEntity<Map<String, Object>> getRequest(@PathVariable String uuid) {
        List<Task> tasks = taskService.findByUuid(uuid);
        if (tasks.isEmpty()) return ResponseEntity.notFound().build();

        tasks.sort((a, b) -> Integer.compare(a.getSequence(), b.getSequence()));

        boolean complete = tasks.stream().anyMatch(t -> t.getType() == 4 && t.getState() == TaskState.COMPLETED);
        boolean failed   = tasks.stream().anyMatch(t -> t.getState() == TaskState.FAILED);
        String  status   = failed ? "FAILED" : complete ? "COMPLETED" : "IN_PROGRESS";

        return ResponseEntity.ok(Map.of(
            "uuid", uuid, "status", status, "steps", tasks.size(), "tasks", tasks
        ));
    }

    /** GET /api/tasks/stats — count tasks per state */
    @GetMapping("/tasks/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(Map.of(
            "pending",   taskRepository.countByState(TaskState.PENDING),
            "running",   taskRepository.countByState(TaskState.RUNNING),
            "completed", taskRepository.countByState(TaskState.COMPLETED),
            "failed",    taskRepository.countByState(TaskState.FAILED)
        ));
    }
}
