package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CompanyVendor;
import com.wrapzzo.backend.model.CompanyVendorId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyVendorRepository extends JpaRepository<CompanyVendor, CompanyVendorId> {

    List<CompanyVendor> findByCompany_IdOrderByCreatedAtDesc(UUID companyId);

    @Query("select cv.vendor.id from CompanyVendor cv where cv.company.id = :companyId")
    List<UUID> findVendorIdsByCompanyId(@Param("companyId") UUID companyId);

    void deleteByCompany_IdAndVendor_Id(UUID companyId, UUID vendorId);
}
