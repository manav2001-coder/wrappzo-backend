package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Role;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    List<Role> findByCompanyId(UUID companyId);

    List<Role> findByCompanyIdIsNull();
}
