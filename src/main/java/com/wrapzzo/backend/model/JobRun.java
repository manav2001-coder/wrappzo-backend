package com.wrapzzo.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** One run of a background job: the unit the Background jobs screen lists, retries and clears. */
@Entity
@Table(name = "job_runs")
@Getter
@Setter
@NoArgsConstructor
public class JobRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "job_name", nullable = false)
    private String jobName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private JobRunStatus status;

    /** "Schedule", or the name of the Super Admin who ran it again. */
    @Column(name = "triggered_by", nullable = false)
    private String triggeredBy;

    @Column(nullable = false)
    private int attempts = 1;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    private String result;

    private String error;

    public JobRun(String jobName, String triggeredBy) {
        this.jobName = jobName;
        this.triggeredBy = triggeredBy;
        this.status = JobRunStatus.RUNNING;
        this.startedAt = Instant.now();
    }
}
