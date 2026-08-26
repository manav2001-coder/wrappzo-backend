package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.AudienceOptionsResponse;
import com.wrapzzo.backend.dto.CampaignAudienceRequest;
import com.wrapzzo.backend.dto.CampaignAudienceResponse;
import com.wrapzzo.backend.dto.CampaignFieldRequest;
import com.wrapzzo.backend.dto.CampaignFieldResponse;
import com.wrapzzo.backend.dto.CampaignResponse;
import com.wrapzzo.backend.dto.CreateCampaignRequest;
import com.wrapzzo.backend.dto.UpdateCampaignRequest;
import com.wrapzzo.backend.exception.CampaignAudienceRequiredException;
import com.wrapzzo.backend.exception.CampaignFieldsLockedException;
import com.wrapzzo.backend.exception.CampaignNotFoundException;
import com.wrapzzo.backend.model.Campaign;
import com.wrapzzo.backend.model.CampaignAudience;
import com.wrapzzo.backend.model.CampaignAudienceType;
import com.wrapzzo.backend.model.CampaignField;
import com.wrapzzo.backend.model.CampaignForm;
import com.wrapzzo.backend.model.CampaignStatus;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.SubmissionStatus;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CampaignAudienceRepository;
import com.wrapzzo.backend.repository.CampaignFieldRepository;
import com.wrapzzo.backend.repository.CampaignFormRepository;
import com.wrapzzo.backend.repository.CampaignOrderCount;
import com.wrapzzo.backend.repository.CampaignRepository;
import com.wrapzzo.backend.repository.CampaignSubmissionStat;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.SubmissionRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignFormRepository campaignFormRepository;
    private final CampaignFieldRepository campaignFieldRepository;
    private final CampaignAudienceRepository campaignAudienceRepository;
    private final GiftRepository giftRepository;
    private final UserRepository userRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final SubmissionRepository submissionRepository;
    private final OrderRepository orderRepository;
    private final AccessControlCacheService accessControlCacheService;

    private record CampaignStats(long submissionCount, long pendingApprovalCount, long orderCount) {
        static final CampaignStats EMPTY = new CampaignStats(0, 0, 0);
    }

    @Transactional
    public CampaignResponse create(CreateCampaignRequest request) {
        User creator = currentUser();
        Company company = creator.getCompany();
        Set<Gift> gifts = new HashSet<>(giftRepository.findAllById(request.giftIds()));

        Campaign toSave = new Campaign(
                company, request.name(), request.purpose(), request.recipientCategory(), gifts, request.instructions(),
                request.submissionDeadline(), creator);
        // saveAndFlush -- campaignNo is DB-generated and only readable after the physical INSERT runs.
        Campaign campaign = campaignRepository.saveAndFlush(toSave);

        CampaignForm form = campaignFormRepository.save(new CampaignForm(campaign));
        int order = 0;
        for (DefaultCampaignFields.Def def : DefaultCampaignFields.DEFAULTS) {
            campaignFieldRepository.save(
                    new CampaignField(form, def.key(), def.label(), def.type(), def.required(), null, List.of(), order++));
        }

        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public List<CampaignResponse> listForCurrentUser() {
        User user = currentUser();
        List<Campaign> campaigns = campaignRepository.findByCompanyIdOrderByCreatedAtDesc(user.getCompany().getId());
        List<Campaign> visible = canManage(user)
                ? campaigns
                : campaigns.stream().filter(c -> c.getStatus() != CampaignStatus.DRAFT && isInAudience(c, user.getId())).toList();
        Map<UUID, CampaignStats> stats = statsFor(visible.stream().map(Campaign::getId).toList());
        return visible.stream().map(c -> toResponse(c, stats.getOrDefault(c.getId(), CampaignStats.EMPTY))).toList();
    }

    @Transactional(readOnly = true)
    public CampaignResponse get(UUID id) {
        return toResponse(getVisibleEntity(id));
    }

    @Transactional
    public CampaignResponse update(UUID id, UpdateCampaignRequest request) {
        Campaign campaign = getEntity(id);
        campaign.setName(request.name());
        campaign.setPurpose(request.purpose());
        campaign.setRecipientCategory(request.recipientCategory());
        campaign.setGifts(new HashSet<>(giftRepository.findAllById(request.giftIds())));
        campaign.setInstructions(request.instructions());
        campaign.setSubmissionDeadline(request.submissionDeadline());
        campaign.setUpdatedBy(currentUser());
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse activate(UUID id) {
        Campaign campaign = getEntity(id);
        List<CampaignAudience> audiences = campaignAudienceRepository.findByCampaignId(id);
        if (audiences.isEmpty()) {
            throw new CampaignAudienceRequiredException(id);
        }
        campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setUpdatedBy(currentUser());
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse close(UUID id) {
        Campaign campaign = getEntity(id);
        campaign.setStatus(CampaignStatus.CLOSED);
        campaign.setUpdatedBy(currentUser());
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse replaceFields(UUID id, List<CampaignFieldRequest> fields) {
        Campaign campaign = getEntity(id);
        if (campaign.getStatus() != CampaignStatus.DRAFT) {
            throw new CampaignFieldsLockedException(id);
        }
        CampaignForm form = campaignFormRepository.findByCampaignId(id).orElseThrow(() -> new CampaignNotFoundException(id));
        campaignFieldRepository.deleteAll(campaignFieldRepository.findByCampaignFormIdOrderByDisplayOrderAsc(form.getId()));
        int order = 0;
        for (CampaignFieldRequest f : fields) {
            campaignFieldRepository.save(
                    new CampaignField(form, f.fieldKey(), f.label(), f.fieldType(), f.required(), f.placeholder(), f.options(), order++));
        }
        campaign.setUpdatedBy(currentUser());
        return toResponse(campaign);
    }

    @Transactional
    public CampaignResponse replaceAudience(UUID id, List<CampaignAudienceRequest> audiences) {
        Campaign campaign = getEntity(id);
        campaignAudienceRepository.deleteByCampaignId(id);
        for (CampaignAudienceRequest a : audiences) {
            OrgUnit orgUnit = a.orgUnitId() != null
                    ? orgUnitRepository.findById(a.orgUnitId()).orElseThrow()
                    : null;
            User anchorUser = a.anchorUserId() != null ? userRepository.findById(a.anchorUserId()).orElseThrow() : null;
            User user = a.userId() != null ? userRepository.findById(a.userId()).orElseThrow() : null;
            campaignAudienceRepository.save(new CampaignAudience(campaign, a.type(), orgUnit, anchorUser, user));
        }
        campaign.setUpdatedBy(currentUser());
        return toResponse(campaign);
    }

    @Transactional(readOnly = true)
    public AudienceOptionsResponse audienceOptions() {
        User user = currentUser();
        UUID companyId = user.getCompany().getId();
        List<AudienceOptionsResponse.OrgUnitOption> orgUnits = orgUnitRepository.findByCompanyId(companyId).stream()
                .map(u -> new AudienceOptionsResponse.OrgUnitOption(u.getId(), u.getName(), u.getLevelName()))
                .toList();
        List<AudienceOptionsResponse.UserOption> users = userRepository.findByCompanyId(companyId).stream()
                .map(u -> new AudienceOptionsResponse.UserOption(u.getId(), u.getName(), u.getEmail()))
                .toList();
        return new AudienceOptionsResponse(orgUnits, users);
    }

    // --- Audience resolution, reused by SubmissionService for authorization checks. ---

    @Transactional(readOnly = true)
    public boolean isInAudience(Campaign campaign, UUID userId) {
        return resolveAudienceUserIds(campaign.getId()).contains(userId);
    }

    @Transactional(readOnly = true)
    public Set<UUID> resolveAudienceUserIds(UUID campaignId) {
        List<CampaignAudience> audiences = campaignAudienceRepository.findByCampaignId(campaignId);
        Set<UUID> result = new HashSet<>();
        for (CampaignAudience audience : audiences) {
            switch (audience.getType()) {
                case ALL_COMPANY_USERS -> {
                    Campaign campaign = audience.getCampaign();
                    userRepository.findByCompanyId(campaign.getCompany().getId()).forEach(u -> result.add(u.getId()));
                }
                case ORG_UNIT -> {
                    List<OrgUnit> units = orgUnitRepository.findSelfAndDescendants(audience.getOrgUnit().getId());
                    List<UUID> unitIds = units.stream().map(OrgUnit::getId).toList();
                    userRepository.findByOrgUnitIdIn(unitIds).forEach(u -> result.add(u.getId()));
                }
                case MANAGER_TEAM -> result.addAll(findAllReportIds(audience.getAnchorUser().getId()));
                case SELECTED_USERS -> result.add(audience.getUser().getId());
            }
        }
        return result;
    }

    /** BFS over User.reportsTo -- bounded by real org depth, a few hundred users per company at most. */
    public Set<UUID> findAllReportIds(UUID managerId) {
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> queue = new ArrayDeque<>(List.of(managerId));
        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            for (User report : userRepository.findByReportsToId(current)) {
                if (visited.add(report.getId())) {
                    queue.add(report.getId());
                }
            }
        }
        return visited;
    }

    public boolean canManage(User user) {
        return accessControlCacheService.resolveEffectiveKeys(user).contains("campaign.manage");
    }

    // --- internal ---

    Campaign getEntity(UUID id) {
        Campaign campaign = campaignRepository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
        if (!campaign.getCompany().getId().equals(currentUser().getCompany().getId())) {
            throw new CampaignNotFoundException(id);
        }
        return campaign;
    }

    /** Same as getEntity, but a non-manager may only see published (non-Draft) campaigns they're in the audience of. */
    private Campaign getVisibleEntity(UUID id) {
        Campaign campaign = getEntity(id);
        User user = currentUser();
        if (canManage(user)) {
            return campaign;
        }
        if (campaign.getStatus() == CampaignStatus.DRAFT || !isInAudience(campaign, user.getId())) {
            throw new CampaignNotFoundException(id);
        }
        return campaign;
    }

    User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    private CampaignResponse toResponse(Campaign campaign) {
        CampaignStats stats = statsFor(List.of(campaign.getId())).getOrDefault(campaign.getId(), CampaignStats.EMPTY);
        return toResponse(campaign, stats);
    }

    private CampaignResponse toResponse(Campaign campaign, CampaignStats stats) {
        List<CampaignFieldResponse> fields = campaignFormRepository.findByCampaignId(campaign.getId())
                .map(form -> campaignFieldRepository.findByCampaignFormIdOrderByDisplayOrderAsc(form.getId()).stream()
                        .map(CampaignFieldResponse::from)
                        .toList())
                .orElse(new ArrayList<>());
        List<CampaignAudienceResponse> audiences = campaignAudienceRepository.findByCampaignId(campaign.getId()).stream()
                .map(CampaignAudienceResponse::from)
                .toList();
        return CampaignResponse.from(
                campaign, fields, audiences, stats.submissionCount(), stats.pendingApprovalCount(), stats.orderCount());
    }

    /** Batched dashboard aggregation (PRD 4.7) -- one query for submissions, one for orders, not per-campaign. */
    private Map<UUID, CampaignStats> statsFor(List<UUID> campaignIds) {
        if (campaignIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, long[]> submissionCounts = new HashMap<>();
        for (CampaignSubmissionStat stat : submissionRepository.countByCampaignIdInGroupByStatus(campaignIds)) {
            long[] counts = submissionCounts.computeIfAbsent(stat.getCampaignId(), k -> new long[2]);
            counts[0] += stat.getCnt();
            if (stat.getStatus() == SubmissionStatus.SUBMITTED || stat.getStatus() == SubmissionStatus.RESUBMITTED) {
                counts[1] += stat.getCnt();
            }
        }
        Map<UUID, Long> orderCounts = new HashMap<>();
        for (CampaignOrderCount count : orderRepository.countByCampaignIdIn(campaignIds)) {
            orderCounts.put(count.getCampaignId(), count.getCnt());
        }
        Map<UUID, CampaignStats> result = new HashMap<>();
        for (UUID id : campaignIds) {
            long[] counts = submissionCounts.getOrDefault(id, new long[2]);
            result.put(id, new CampaignStats(counts[0], counts[1], orderCounts.getOrDefault(id, 0L)));
        }
        return result;
    }
}
