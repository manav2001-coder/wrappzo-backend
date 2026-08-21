package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.RoleAccessControl;
import com.wrapzzo.backend.model.RoleAccessControlId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoleAccessControlRepository extends JpaRepository<RoleAccessControl, RoleAccessControlId> {

    List<RoleAccessControl> findByRoleId(UUID roleId);

    @Query("select rac.accessControl.key from RoleAccessControl rac where rac.role.id = :roleId")
    List<String> findAccessControlKeysByRoleId(@Param("roleId") UUID roleId);
}
