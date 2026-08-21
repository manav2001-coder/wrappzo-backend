package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.UserAccessControl;
import com.wrapzzo.backend.model.UserAccessControlId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccessControlRepository extends JpaRepository<UserAccessControl, UserAccessControlId> {

    List<UserAccessControl> findByUserId(UUID userId);

    @Query("select uac.accessControl.key as key, uac.effect as effect from UserAccessControl uac where uac.user.id = :userId")
    List<UserAccessKeyEffect> findKeyEffectsByUserId(@Param("userId") UUID userId);
}
