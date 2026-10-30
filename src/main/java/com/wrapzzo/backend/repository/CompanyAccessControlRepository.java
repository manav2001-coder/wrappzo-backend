package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CompanyAccessControl;
import com.wrapzzo.backend.model.CompanyAccessControlId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyAccessControlRepository extends JpaRepository<CompanyAccessControl, CompanyAccessControlId> {

    @Query("select c from CompanyAccessControl c where c.company.id = :companyId")
    List<CompanyAccessControl> findByCompanyId(@Param("companyId") UUID companyId);

    @Query("select c from CompanyAccessControl c where c.company.id = :companyId and c.accessControl.id = :accessControlId")
    java.util.Optional<CompanyAccessControl> findByCompanyIdAndAccessControlId(
            @Param("companyId") UUID companyId, @Param("accessControlId") UUID accessControlId);

    @Query("select c.accessControl.key from CompanyAccessControl c where c.company.id = :companyId and c.active = true")
    List<String> findActiveKeysByCompanyId(@Param("companyId") UUID companyId);

    @Query("select c.accessControl.id from CompanyAccessControl c where c.company.id = :companyId and c.active = true")
    List<UUID> findActiveAccessControlIdsByCompanyId(@Param("companyId") UUID companyId);
}
