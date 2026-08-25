package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.SubmissionValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionValueRepository extends JpaRepository<SubmissionValue, UUID> {

    List<SubmissionValue> findBySubmissionId(UUID submissionId);

    Optional<SubmissionValue> findBySubmissionIdAndCampaignFieldFieldKey(UUID submissionId, String fieldKey);
}
