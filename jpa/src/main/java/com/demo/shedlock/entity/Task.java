package com.demo.shedlock.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Represents a unit of work to be processed by a provision thread.
 *
 * State machine:
 *   PENDING → RUNNING → COMPLETED
 *                     → FAILED
 */
@Entity
@Table(
    name = "tasks",
    indexes = {
        @Index(name = "idx_tasks_state",      columnList = "state"),
        @Index(name = "idx_tasks_uuid_seq",   columnList = "uuid, sequence")
    },
    uniqueConstraints = {
        @UniqueConstraint(
            name = "uk_task_uuid_sequence_type",
            columnNames = {"uuid", "sequence", "type"}
        )
    }
)
@Data
@NoArgsConstructor
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Request UUID this task belongs to */
    @Column(nullable = false, length = 64)
    private String uuid;

    /** Step number within the workflow */
    @Column(nullable = false)
    private int sequence;

    /**
     * Task type — maps to a specific provision thread handler.
     * 1 = PROVISION_VM
     * 2 = INSTALL_SOFTWARE
     * 3 = CONFIGURE_NETWORK
     * 4 = HEALTH_CHECK
     */
    @Column(nullable = false)
    private int type;

    @Column(nullable = false, length = 255)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TaskState state = TaskState.PENDING;

    /** Which pod/thread picked up this task */
    @Column(length = 255)
    private String processedBy;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column
    private Instant startedAt;

    @Column
    private Instant completedAt;

    @Column(length = 1000)
    private String errorMessage;

    /** Retry count */
    @Column(nullable = false)
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
