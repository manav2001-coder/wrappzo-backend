package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.DesignationAccessControl;
import com.wrapzzo.backend.model.DesignationAccessControlId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DesignationAccessControlRepository extends JpaRepository<DesignationAccessControl, DesignationAccessControlId> {

    @Query(
            "select dac.accessControl.key from DesignationAccessControl dac "
                    + "where dac.company.id = :companyId and dac.id.designation = :designation")
    List<String> findAccessControlKeysByCompanyIdAndDesignation(@Param("companyId") UUID companyId, @Param("designation") String designation);

    @Query(
            "select dac.accessControl.id from DesignationAccessControl dac "
                    + "where dac.company.id = :companyId and dac.id.designation = :designation")
    List<UUID> findAccessControlIdsByCompanyIdAndDesignation(@Param("companyId") UUID companyId, @Param("designation") String designation);

    @Modifying
    @Query("delete from DesignationAccessControl dac where dac.company.id = :companyId and dac.id.designation = :designation")
    void deleteByCompanyIdAndDesignation(@Param("companyId") UUID companyId, @Param("designation") String designation);
}
