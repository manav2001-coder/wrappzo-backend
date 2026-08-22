package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.UserInviteToken;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserInviteTokenRepository extends JpaRepository<UserInviteToken, UUID> {

    Optional<UserInviteToken> findByTokenHash(String tokenHash);

    /** Called before issuing a fresh invite, so a user never has two simultaneously valid setup links. */
    @Modifying
    @Query("delete from UserInviteToken t where t.user.id = :userId and t.usedAt is null")
    void deleteUnusedByUserId(@Param("userId") UUID userId);

    /**
     * Same, batched -- a bulk import re-inviting thousands of still-INVITED users in one
     * call needs one DELETE, not one per user. A @Modifying query auto-flushes the whole
     * pending session before running, so doing this per-row inside a loop turns every
     * other pending insert in that session into repeated, ever-larger flush work.
     */
    @Modifying
    @Query("delete from UserInviteToken t where t.user.id in :userIds and t.usedAt is null")
    void deleteUnusedByUserIdIn(@Param("userIds") Collection<UUID> userIds);
}
