# ShedLock + Multi-Thread Task Processing Demo

Spring Boot project demonstrating distributed singleton scheduling with ShedLock (JPA provider)
and a multi-threaded task worker pattern.

## Architecture

```
POST /api/requests
        │
        ▼
  TaskController
  saveIfNotExists(Task type=1, seq=1)   ← seeds first task
        │
        ▼ (every 5s, one pod at a time)
  TaskScheduler  ──── @SchedulerLock ────► shedlock table (DB lock)
        │
        ├── fetchPendingTasks(batchSize=10)
        │
        └── for each task:
              claimTask() [atomic UPDATE WHERE state=PENDING]
                    │
                    ▼
            ProvisionThreadFactory.create(task)
                    │
                    ├── type=1 → ProvisionVmThread      → triggers seq=2 type=2
                    ├── type=2 → InstallSoftwareThread   → triggers seq=3 type=3
                    ├── type=3 → ConfigureNetworkThread  → triggers seq=4 type=4
                    └── type=4 → HealthCheckThread       → (terminal)
```

## Key Patterns

### 1. ShedLock — distributed singleton
Only one pod runs `checkTasks()` at a time, even across N Kubernetes pods.

```java
@Scheduled(fixedDelay = 5000)
@SchedulerLock(name = "TaskScheduler_checkTasks",
               lockAtLeastFor = "4s", lockAtMostFor = "4500ms")
public void checkTasks() { ... }
```

**Timing rule: `lockAtMostFor` < `fixedDelay`** (4500ms < 5000ms ✅)

### 2. Atomic task claim — no double-processing
Even after ShedLock picks one pod, the thread pool inside that pod uses
an atomic `UPDATE WHERE state=PENDING` so no two threads process the same task.

```java
@Modifying
@Query("UPDATE Task t SET t.state='RUNNING', t.processedBy=:processedBy
        WHERE t.id=:id AND t.state='PENDING'")
int claimTask(@Param("id") Long id, @Param("processedBy") String processedBy);
```

### 3. saveIfNotExists — duplicate-safe task creation
Handles race conditions when multiple threads try to create the same next task.

```java
findByUuidAndSequenceAndType(...)   // check
  → save()                          // insert
  → catch DataIntegrityViolationException  // race fallback
  → findByUuidAndSequenceAndType(...)      // return existing
```

### 4. BaseProvisionThread — extensible worker pattern
```java
public class MyNewThread extends BaseProvisionThread {
    @Override
    protected void execute() throws Exception {
        // your logic
        triggerNextTask(nextSeq, nextType, "description");
    }
}
```
Add a `case` in `ProvisionThreadFactory` — done.

## Running

```bash
# H2 (default)
mvn spring-boot:run

# PostgreSQL (optional, use Postgres 18 on db-host)
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

### Create a workflow
```bash
curl -X POST http://localhost:8080/api/requests \
  -H "Content-Type: application/json" \
  -d '{"label": "my-server-01"}'
```

### Track progress
```bash
curl http://localhost:8080/api/requests/{uuid}
```

### Stats dashboard
```bash
curl http://localhost:8080/api/tasks/stats
```

### H2 Console
http://localhost:8080/h2-console  
JDBC URL: `jdbc:h2:mem:taskdb`

## Configuration (`application.yml`)

| Property | Default | Description |
|---|---|---|
| `task.scheduler.pick-interval-ms` | 5000 | Scheduler poll interval |
| `task.scheduler.thread-pool-size` | 4 | Worker threads per pod |
| `task.scheduler.batch-size` | 10 | Tasks picked per scheduler run |

## ShedLock Timing Reference

| Scheduler | fixedDelay | lockAtLeastFor | lockAtMostFor |
|---|---|---|---|
| TaskScheduler | 5000ms | 4s | 4500ms ✅ |

## Adding a New Task Type

1. Create `YourThread extends BaseProvisionThread`
2. Override `execute()`, call `triggerNextTask()` if needed
3. Add `case N -> new YourThread(task, taskService)` in `ProvisionThreadFactory`
4. That's it — no scheduler changes needed

## Testing 
```bash
# 10 requests (default)
./test_tasks.sh

# 20 requests
./test_tasks.sh 20

# 20 requests on custom port
./test_tasks.sh 20 localhost:9090

# 15 requests + watch progress live until all 4 steps complete
./test_tasks.sh 15 localhost:8080 true
```
