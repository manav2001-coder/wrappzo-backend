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

    /**
     * Puts a doctor on a list, or does nothing if the doctor is already there. One statement, so two requests adding the
     * same pair at the same moment cannot both pass a "not there yet" check and then collide on the unique rule.
     * Returns 1 if it added the doctor, 0 if the doctor was already on the list.
     */
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = "insert into recipient_assignments (id, recipient_id, mr_user_id, assigned_at) "
            + "values (gen_random_uuid(), :recipientId, :userId, now()) on conflict (recipient_id, mr_user_id) do nothing", nativeQuery = true)
    int addIfAbsent(@org.springframework.data.repository.query.Param("recipientId") UUID recipientId,
            @org.springframework.data.repository.query.Param("userId") UUID userId);
}
