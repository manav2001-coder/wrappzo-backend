package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.RecipientAssignment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecipientAssignmentRepository extends JpaRepository<RecipientAssignment, UUID> {

    List<RecipientAssignment> findByMrUser_IdOrderByAssignedAtDesc(UUID mrUserId);

    Optional<RecipientAssignment> findByRecipient_IdAndMrUser_Id(UUID recipientId, UUID mrUserId);

    /** Which of these doctors are on the user's own list -- for a page of a list, not all of it. */
    @org.springframework.data.jpa.repository.Query("select a.recipient.id from RecipientAssignment a where a.mrUser.id = :userId and a.recipient.id in :recipientIds")
    List<UUID> findRecipientIdsOnList(@org.springframework.data.repository.query.Param("userId") UUID userId,
            @org.springframework.data.repository.query.Param("recipientIds") java.util.Collection<UUID> recipientIds);

    boolean existsByRecipient_IdAndMrUser_Id(UUID recipientId, UUID mrUserId);
}
