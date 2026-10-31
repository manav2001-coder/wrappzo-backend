package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.JobRun;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobRunRepository extends JpaRepository<JobRun, UUID> {

    List<JobRun> findAllByOrderByStartedAtDesc(Pageable limit);

    @Modifying
    @Query("delete from JobRun r where r.jobName = :jobName and r.status <> com.wrapzzo.backend.model.JobRunStatus.RUNNING and r.startedAt < :cutoff")
    int deleteOlderThan(@Param("jobName") String jobName, @Param("cutoff") Instant cutoff);
}
