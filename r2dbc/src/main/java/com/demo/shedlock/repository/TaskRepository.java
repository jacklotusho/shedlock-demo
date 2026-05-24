package com.demo.shedlock.repository;

import com.demo.shedlock.entity.Task;
import org.springframework.data.r2dbc.repository.Modifying;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Reactive R2DBC repository.
 *
 * Two fixes vs previous version:
 * 1. All @Query params use @Param — R2DBC requires explicit binding names.
 * 2. Timestamps use LocalDateTime instead of Instant — H2/PostgreSQL R2DBC
 *    drivers map TIMESTAMP columns to LocalDateTime, not Instant, so binding
 *    Instant causes "no converter found" and tasks never get claimed/completed.
 * 3. findPendingTasks uses TOP :n (H2) / LIMIT inline — avoid binding LIMIT
 *    as a named param (not supported in all R2DBC drivers).
 */
@Repository
public interface TaskRepository extends ReactiveCrudRepository<Task, Long> {

    /**
     * Fetch PENDING tasks oldest-first.
     * Uses Spring Data method name — no native SQL needed, works on all DBs.
     */
    Flux<Task> findByStateOrderByCreatedAtAsc(String state);

    /** Find by unique key — used by saveIfNotExists */
    @Query("SELECT * FROM tasks WHERE uuid = :uuid AND sequence = :sequence AND type = :type LIMIT 1")
    Mono<Task> findByUuidAndSequenceAndType(
        @Param("uuid")     String uuid,
        @Param("sequence") int    sequence,
        @Param("type")     int    type);

    /**
     * Atomic claim: PENDING → RUNNING only if still PENDING.
     * Returns rows updated: 1 = claimed, 0 = already taken by another worker.
     */
    @Modifying
    @Query("""
        UPDATE tasks
        SET state = 'RUNNING',
            processed_by = :processedBy,
            started_at   = :startedAt
        WHERE id = :id AND state = 'PENDING'
        """)
    Mono<Integer> claimTask(
        @Param("id")          Long          id,
        @Param("processedBy") String        processedBy,
        @Param("startedAt")   LocalDateTime startedAt);

    @Modifying
    @Query("UPDATE tasks SET state = 'COMPLETED', completed_at = :completedAt WHERE id = :id")
    Mono<Integer> completeTask(
        @Param("id")          Long          id,
        @Param("completedAt") LocalDateTime completedAt);

    @Modifying
    @Query("""
        UPDATE tasks
        SET state         = 'FAILED',
            completed_at  = :completedAt,
            error_message = :errorMessage,
            retry_count   = retry_count + 1
        WHERE id = :id
        """)
    Mono<Integer> failTask(
        @Param("id")           Long          id,
        @Param("completedAt")  LocalDateTime completedAt,
        @Param("errorMessage") String        errorMessage);

    Flux<Task> findByUuid(String uuid);

    Mono<Long> countByState(String state);
}
