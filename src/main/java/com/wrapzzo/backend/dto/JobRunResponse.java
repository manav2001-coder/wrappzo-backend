package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.JobRun;
import com.wrapzzo.backend.model.JobRunStatus;
import java.time.Instant;
import java.util.UUID;

public record JobRunResponse(
        UUID id,
        String jobName,
        String label,
        JobRunStatus status,
        String triggeredBy,
        int attempts,
        Instant startedAt,
        Instant finishedAt,
        String result,
        String error) {

    public static JobRunResponse from(JobRun run, String label) {
        return new JobRunResponse(
                run.getId(), run.getJobName(), label, run.getStatus(), run.getTriggeredBy(), run.getAttempts(),
                run.getStartedAt(), run.getFinishedAt(), run.getResult(), run.getError());
    }
}
