package com.demo.shedlock.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * Spring Data R2DBC entity.
 *
 * Uses LocalDateTime (not Instant) for all timestamp fields.
 * R2DBC drivers (H2 + PostgreSQL) map SQL TIMESTAMP → LocalDateTime.
 * Using Instant causes "no suitable converter" errors and silent failures
 * on UPDATE queries (claimTask, completeTask, failTask never apply).
 */
@Table("tasks")
@Data
@NoArgsConstructor
public class Task {

    @Id
    private Long id;

    private String uuid;
    private int    sequence;
    private int    type;
    private String description;

    private String state = TaskState.PENDING.name();

    @Column("processed_by")
    private String processedBy;

    @Column("created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column("started_at")
    private LocalDateTime startedAt;

    @Column("completed_at")
    private LocalDateTime completedAt;

    @Column("error_message")
    private String errorMessage;

    @Column("retry_count")
    private int retryCount = 0;

    public enum TaskState {
        PENDING, RUNNING, COMPLETED, FAILED
    }

    public Task(String uuid, int sequence, int type, String description) {
        this.uuid        = uuid;
        this.sequence    = sequence;
        this.type        = type;
        this.description = description;
    }
}
