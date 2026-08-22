package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.UserInviteToken;
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
}
