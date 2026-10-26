package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.CompanyFeatureFlag;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyFeatureFlagRepository extends JpaRepository<CompanyFeatureFlag, UUID> {

    List<CompanyFeatureFlag> findByCompany_Id(UUID companyId);

    Optional<CompanyFeatureFlag> findByCompany_IdAndFlagKey(UUID companyId, CompanyFeature flagKey);
}
