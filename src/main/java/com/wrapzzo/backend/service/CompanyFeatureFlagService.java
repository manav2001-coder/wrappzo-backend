package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.ArtworkApprovalLevelsResponse;
import com.wrapzzo.backend.dto.ArtworkApprovalStepsRequest;
import com.wrapzzo.backend.dto.ArtworkApprovalStepsResponse;
import com.wrapzzo.backend.dto.CompanyFeatureFlagResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyArtworkApprovalStep;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.exception.InvalidArtworkApprovalStepsException;
import com.wrapzzo.backend.model.CompanyFeatureFlag;
import com.wrapzzo.backend.repository.CompanyArtworkApprovalStepRepository;
import com.wrapzzo.backend.repository.CompanyFeatureFlagRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-company on/off switches for optional workflow changes. No row for a given
 * (company, flag) means off -- every caller goes through isEnabled() rather than assuming
 * a row exists, so a brand-new company (or a brand-new flag added later) is safely off by
 * default without a backfill migration.
 */
@Service
@RequiredArgsConstructor
public class CompanyFeatureFlagService {

    private final CompanyFeatureFlagRepository companyFeatureFlagRepository;
    private final CompanyRepository companyRepository;
    private final CompanyArtworkApprovalStepRepository approvalStepRepository;
    private final RoleRepository roleRepository;

    @Transactional(readOnly = true)
    public boolean isEnabled(UUID companyId, CompanyFeature feature) {
        return companyFeatureFlagRepository.findByCompany_IdAndFlagKey(companyId, feature).map(CompanyFeatureFlag::isEnabled).orElse(false);
    }

    /** Every known flag for this company, defaulted off for anything with no row yet -- so the admin UI always shows the complete, current set. */
    @Transactional(readOnly = true)
    public List<CompanyFeatureFlagResponse> list(UUID companyId) {
        Map<CompanyFeature, Boolean> existing = companyFeatureFlagRepository.findByCompany_Id(companyId).stream()
                .collect(Collectors.toMap(CompanyFeatureFlag::getFlagKey, CompanyFeatureFlag::isEnabled));
        return Arrays.stream(CompanyFeature.values())
                .map(flag -> CompanyFeatureFlagResponse.of(flag, existing.getOrDefault(flag, false)))
                .toList();
    }

    @Transactional
    public List<CompanyFeatureFlagResponse> setEnabled(UUID companyId, CompanyFeature flag, boolean enabled) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        CompanyFeatureFlag entity = companyFeatureFlagRepository
                .findByCompany_IdAndFlagKey(companyId, flag)
                .orElseGet(() -> new CompanyFeatureFlag(company, flag, enabled));
        entity.setEnabled(enabled);
        companyFeatureFlagRepository.save(entity);
        return list(companyId);
    }

    /** How many managers above the MR sign off on artwork while MARKETING_DESIGN_APPROVAL is on (companies.artwork_approval_levels). */
    @Transactional(readOnly = true)
    public ArtworkApprovalLevelsResponse getArtworkApprovalLevels(UUID companyId) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        return new ArtworkApprovalLevelsResponse(company.getArtworkApprovalLevels());
    }

    /** Applies from each proof's next step: a proof already climbing re-reads the level count as it moves up. */
    @Transactional
    public ArtworkApprovalLevelsResponse setArtworkApprovalLevels(UUID companyId, int levels) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        company.setArtworkApprovalLevels(levels);
        return new ArtworkApprovalLevelsResponse(company.getArtworkApprovalLevels());
    }

    /** The company's approval steps after the AM's own review, in order; empty means the simple "N managers up the line" mode. */
    @Transactional(readOnly = true)
    public ArtworkApprovalStepsResponse getArtworkApprovalSteps(UUID companyId) {
        return new ArtworkApprovalStepsResponse(approvalStepRepository.findByCompany_IdOrderByStepNoAsc(companyId).stream()
                .map(step -> new ArtworkApprovalStepsResponse.Step(step.getStepNo(), step.getRole().getId(), step.getRole().getName(), step.getScope()))
                .toList());
    }

    /**
     * Replaces the steps. Applies from each proof's next move: a proof already climbing re-reads the chain as it goes.
     * The simple level count follows the number of steps, so the two never disagree.
     */
    @Transactional
    public ArtworkApprovalStepsResponse setArtworkApprovalSteps(UUID companyId, ArtworkApprovalStepsRequest request) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));
        List<CompanyArtworkApprovalStep> steps = new java.util.ArrayList<>();
        int stepNo = 1;
        for (ArtworkApprovalStepsRequest.Step input : request.steps()) {
            Role role = roleRepository.findById(input.roleId())
                    .filter(r -> r.isActive() && r.getCompany() != null && company.getId().equals(r.getCompany().getId()))
                    .orElseThrow(() -> new InvalidArtworkApprovalStepsException("Step " + (steps.size() + 1) + ": pick one of this company's active roles."));
            steps.add(new CompanyArtworkApprovalStep(company, stepNo++, role, input.scope()));
        }
        approvalStepRepository.deleteByCompany_Id(companyId);
        approvalStepRepository.flush();
        approvalStepRepository.saveAll(steps);
        if (!steps.isEmpty()) {
            company.setArtworkApprovalLevels(steps.size());
        }
        return getArtworkApprovalSteps(companyId);
    }
}
