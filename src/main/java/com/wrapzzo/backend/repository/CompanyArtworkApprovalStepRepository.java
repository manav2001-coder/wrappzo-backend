package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CompanyArtworkApprovalStep;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyArtworkApprovalStepRepository extends JpaRepository<CompanyArtworkApprovalStep, UUID> {

    List<CompanyArtworkApprovalStep> findByCompany_IdOrderByStepNoAsc(UUID companyId);

    void deleteByCompany_Id(UUID companyId);
}
