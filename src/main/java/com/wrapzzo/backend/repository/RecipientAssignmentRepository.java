package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.RecipientAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientAssignmentRepository extends JpaRepository<RecipientAssignment, UUID> {

    List<RecipientAssignment> findByMrUser_IdOrderByAssignedAtDesc(UUID mrUserId);

    Optional<RecipientAssignment> findByRecipient_IdAndMrUser_Id(UUID recipientId, UUID mrUserId);

    boolean existsByRecipient_IdAndMrUser_Id(UUID recipientId, UUID mrUserId);
}
