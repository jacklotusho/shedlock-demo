# ShedLock R2DBC + Multi-Thread Task Processing Demo

Spring Boot project with **R2DBC** (reactive DB) + **ShedLock R2DBC provider** + **blocking provision threads**.

## Stack

| Layer | Technology |
|---|---|
| Web | Spring WebFlux (Netty) |
| DB access | Spring Data R2DBC |
| Distributed lock | ShedLock R2DBC provider |
| Schema init | Spring SQL init (JDBC) |
| Default DB | H2 in-memory |
| Production DB | PostgreSQL |
| Worker threads | Java ExecutorService (blocking) |

## Architecture

```
POST /api/requests  (WebFlux — reactive)
        │
        ▼
  TaskController (Mono<ResponseEntity>)
  taskService.saveIfNotExists(Task type=1)
        │
        ▼  every 5s — one pod at a time via ShedLock R2DBC
  TaskPickupScheduler (@Scheduled — blocking thread)
    .fetchPendingTasks().collectList().block()   ← reactive → blocking bridge
        │
        └── for each task:
              .claimTask().block()               ← atomic UPDATE
                    │
                    ▼
            ProvisionThreadFactory.create(task)
            executorService.submit(worker)       ← dispatched to task-worker-N
                    │
                    ├── type=1 → ProvisionVmThread       → triggerNextTask(2)
                    ├── type=2 → InstallSoftwareThread    → triggerNextTask(3)
                    ├── type=3 → ConfigureNetworkThread   → triggerNextTask(4)
                    └── type=4 → HealthCheckThread        (terminal)
```

## Thread Model

```
Netty event loop  →  handles HTTP I/O (non-blocking)
R2DBC event loop  →  handles DB I/O (non-blocking)
scheduling-1      →  @Scheduled thread — bridges reactive→blocking via .block()
task-worker-0..N  →  provision threads — blocking work, call .block() on TaskService
```

`.block()` is safe on `task-worker-N` threads because they are NOT reactor event loop threads.

## Running

```bash
# H2 (default)
mvn spring-boot:run

# PostgreSQL
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## API

```bash
# Create workflow
curl -X POST http://localhost:8080/api/requests \
  -H "Content-Type: application/json" \
  -d '{"label":"server-01"}'

# Track progress
curl http://localhost:8080/api/requests/{uuid}

# Stats
curl http://localhost:8080/api/tasks/stats

# H2 console
open http://localhost:8080/h2-console
# JDBC URL: jdbc:h2:mem:taskdb

# Run test script
chmod +x test_tasks.sh
./test_tasks.sh 10 localhost:8080 true
```
