package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrgUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    List<OrgUnit> findByCompanyId(UUID companyId);
}
