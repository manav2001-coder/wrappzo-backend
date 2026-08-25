package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.SubmissionAttachment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SubmissionAttachmentRepository extends JpaRepository<SubmissionAttachment, UUID> {

    List<SubmissionAttachment> findBySubmissionId(UUID submissionId);
}
