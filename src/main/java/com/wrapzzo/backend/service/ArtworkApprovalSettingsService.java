package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.ArtworkApprovalStepsRequest;
import com.wrapzzo.backend.dto.ArtworkApprovalStepsResponse;
import com.wrapzzo.backend.exception.InvalidArtworkApprovalStepsException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyArtworkApprovalStep;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyArtworkApprovalStepRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who approves artwork after the AM, set by the company's own admin (artwork_approval.configure) rather than by
 * Wrappzo at onboarding: only the company knows its roles. Wrappzo's part is the Senior Artwork Approval switch
 * (MARKETING_DESIGN_APPROVAL); the steps are the company's to define once it is on.
 */
@Service
@RequiredArgsConstructor
public class ArtworkApprovalSettingsService {

    private final CompanyArtworkApprovalStepRepository stepRepository;
    private final CompanyFeatureFlagService companyFeatureFlagService;
    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public ArtworkApprovalStepsResponse get() {
        return respond(callersCompany());
    }

    /**
     * Replaces the steps; an empty list goes back to "N managers up the line". Applies from each proof's next move: a
     * proof already climbing re-reads the chain as it goes. The level count follows the number of steps.
     */
    @Transactional
    public ArtworkApprovalStepsResponse set(ArtworkApprovalStepsRequest request) {
        Company company = callersCompany();
        if (!companyFeatureFlagService.isEnabled(company.getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)) {
            throw new InvalidArtworkApprovalStepsException(
                    "Senior artwork approval is not switched on for your company, so there is nothing to set up. Ask Wrappzo to enable it.");
        }
        List<CompanyArtworkApprovalStep> steps = new ArrayList<>();
        int stepNo = 1;
        for (ArtworkApprovalStepsRequest.Step input : request.steps()) {
            int position = stepNo;
            Role role = roleRepository.findById(input.roleId())
                    .filter(r -> r.isActive() && r.getCompany() != null && company.getId().equals(r.getCompany().getId()))
                    .orElseThrow(() -> new InvalidArtworkApprovalStepsException("Step " + position + ": pick one of your company's active roles."));
            steps.add(new CompanyArtworkApprovalStep(company, stepNo++, role, input.scope()));
        }
        stepRepository.deleteByCompany_Id(company.getId());
        stepRepository.flush();
        stepRepository.saveAll(steps);
        if (!steps.isEmpty()) {
            company.setArtworkApprovalLevels(steps.size());
        }
        return respond(company);
    }

    private ArtworkApprovalStepsResponse respond(Company company) {
        List<ArtworkApprovalStepsResponse.Step> steps = stepRepository.findByCompany_IdOrderByStepNoAsc(company.getId()).stream()
                .map(step -> new ArtworkApprovalStepsResponse.Step(step.getStepNo(), step.getRole().getId(), step.getRole().getName(), step.getScope()))
                .toList();
        return new ArtworkApprovalStepsResponse(
                companyFeatureFlagService.isEnabled(company.getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL),
                company.getArtworkApprovalLevels(),
                steps);
    }

    private Company callersCompany() {
        User user = userRepository.findById(CurrentUser.id()).orElseThrow(() -> new IllegalStateException("Authenticated user not found"));
        if (user.getCompany() == null) {
            throw new AccessDeniedException("Artwork approval belongs to a company; platform staff have none.");
        }
        return user.getCompany();
    }
}
