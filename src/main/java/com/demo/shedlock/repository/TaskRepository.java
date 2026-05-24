package com.demo.shedlock.repository;

import com.demo.shedlock.entity.Task;
import com.demo.shedlock.entity.Task.TaskState;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    @Query("SELECT t FROM Task t WHERE t.state = :state ORDER BY t.createdAt ASC")
    List<Task> findByStateOrderByCreatedAt(@Param("state") TaskState state, Pageable pageable);

    Optional<Task> findByUuidAndSequenceAndType(String uuid, int sequence, int type);

    /**
     * Atomic claim: PENDING → RUNNING.
     * clearAutomatically = true flushes the 1st-level cache so callers
     * immediately see the updated state without a stale entity.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
        UPDATE Task t
        SET t.state = 'RUNNING', t.processedBy = :processedBy, t.startedAt = :startedAt
        WHERE t.id = :id AND t.state = 'PENDING'
        """)
    int claimTask(@Param("id") Long id,
                  @Param("processedBy") String processedBy,
                  @Param("startedAt") Instant startedAt);

    List<Task> findByUuid(String uuid);

    long countByState(TaskState state);
}
