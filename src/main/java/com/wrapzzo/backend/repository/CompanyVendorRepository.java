package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CompanyVendor;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyVendorRepository extends JpaRepository<CompanyVendor, UUID> {

    List<CompanyVendor> findByCompany_IdOrderByCreatedAtDesc(UUID companyId);

    /** Company-wide (orgUnitId null) rows, plus any row scoped to one of the given org unit ids (the viewer's own unit and its ancestors). */
    @Query("""
            select cv.vendor.id from CompanyVendor cv
            where cv.company.id = :companyId
              and (cv.orgUnit is null or cv.orgUnit.id in :orgUnitIds)
            """)
    List<UUID> findVendorIdsVisibleToOrgUnits(@Param("companyId") UUID companyId, @Param("orgUnitIds") List<UUID> orgUnitIds);

    @Query("select distinct cv.vendor.id from CompanyVendor cv where cv.company.id = :companyId")
    List<UUID> findVendorIdsByCompany(@Param("companyId") UUID companyId);

    boolean existsByCompany_IdAndVendor_Id(UUID companyId, UUID vendorId);

    @Query("select cv.vendor.id from CompanyVendor cv where cv.company.id = :companyId and cv.orgUnit is null")
    List<UUID> findCompanyWideVendorIds(@Param("companyId") UUID companyId);
}
