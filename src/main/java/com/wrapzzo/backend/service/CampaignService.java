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
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.exception.UserNotFoundException;
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
import com.wrapzzo.backend.security.Permission;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Collection;
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
    private final GiftService giftService;

    private record CampaignStats(long submissionCount, long pendingApprovalCount, long orderCount) {
        static final CampaignStats EMPTY = new CampaignStats(0, 0, 0);
    }

    @Transactional
    public CampaignResponse create(CreateCampaignRequest request) {
        User creator = currentUser();
        Company company = creator.getCompany();
        Set<Gift> gifts = offeredGifts(creator, request.giftIds());

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
        List<Campaign> visible = visibleCampaigns(user);
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
        campaign.setGifts(offeredGifts(currentUser(), request.giftIds()));
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
            // Every id here comes from the request: it must be THIS company's, not just exist.
            UUID companyId = campaign.getCompany().getId();
            OrgUnit orgUnit = a.orgUnitId() != null
                    ? orgUnitRepository.findById(a.orgUnitId())
                            .filter(unit -> unit.getCompany().getId().equals(companyId))
                            .orElseThrow(() -> new OrgUnitNotFoundException(a.orgUnitId()))
                    : null;
            User anchorUser = a.anchorUserId() != null ? companyUser(a.anchorUserId(), companyId) : null;
            User user = a.userId() != null ? companyUser(a.userId(), companyId) : null;
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
                .map(u -> new AudienceOptionsResponse.OrgUnitOption(
                        u.getId(), u.getName(), u.getLevelName(), u.getParent() != null ? u.getParent().getId() : null))
                .toList();
        List<AudienceOptionsResponse.UserOption> users = userRepository.findByCompanyId(companyId).stream()
                .map(u -> new AudienceOptionsResponse.UserOption(u.getId(), u.getName(), u.getEmail()))
                .toList();
        return new AudienceOptionsResponse(orgUnits, users);
    }

    private User companyUser(UUID userId, UUID companyId) {
        return userRepository.findById(userId)
                .filter(candidate -> candidate.getCompany() != null && candidate.getCompany().getId().equals(companyId))
                .orElseThrow(() -> new UserNotFoundException(userId));
    }

    /** The gifts a campaign may offer: every one must exist and be in the caller's own Gift Store (the company's vendor roster). */
    private Set<Gift> offeredGifts(User user, Collection<UUID> giftIds) {
        Set<Gift> gifts = new HashSet<>();
        for (UUID giftId : new HashSet<>(giftIds)) {
            Gift gift = giftRepository.findById(giftId).orElseThrow(() -> new GiftNotFoundException(giftId));
            if (!giftService.isInRoster(gift, user)) {
                throw new GiftNotFoundException(giftId);
            }
            gifts.add(gift);
        }
        return gifts;
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

    /** Everyone below the manager in the reporting line, at any depth. */
    public Set<UUID> findAllReportIds(UUID managerId) {
        return new HashSet<>(userRepository.findReportingLineIds(managerId));
    }

    /**
     * The campaigns a user may see: with campaign.view, every campaign -- drafts only for those
     * who can also create, edit or publish them; otherwise only published campaigns they're in
     * the audience of (to submit to them).
     */
    @Transactional(readOnly = true)
    public List<Campaign> visibleCampaigns(User user) {
        List<Campaign> campaigns = campaignRepository.findByCompanyIdOrderByCreatedAtDesc(user.getCompany().getId());
        return campaigns.stream().filter(c -> canSee(user, c)).toList();
    }

    /** Whoever can edit campaigns may also submit to one they aren't in the audience of (e.g. on someone's behalf). */
    public boolean canEditCampaigns(User user) {
        return accessControlCacheService.resolveEffectiveKeys(user).contains(Permission.CAMPAIGN_EDIT.key());
    }

    private boolean canSee(User user, Campaign campaign) {
        Set<String> keys = accessControlCacheService.resolveEffectiveKeys(user);
        if (keys.contains(Permission.CAMPAIGN_VIEW.key())) {
            return campaign.getStatus() != CampaignStatus.DRAFT || canWorkOnDrafts(keys);
        }
        return campaign.getStatus() != CampaignStatus.DRAFT && isInAudience(campaign, user.getId());
    }

    private static boolean canWorkOnDrafts(Set<String> keys) {
        return keys.contains(Permission.CAMPAIGN_CREATE.key())
                || keys.contains(Permission.CAMPAIGN_EDIT.key())
                || keys.contains(Permission.CAMPAIGN_PUBLISH.key());
    }

    // --- internal ---

    Campaign getEntity(UUID id) {
        Campaign campaign = campaignRepository.findById(id).orElseThrow(() -> new CampaignNotFoundException(id));
        if (!campaign.getCompany().getId().equals(currentUser().getCompany().getId())) {
            throw new CampaignNotFoundException(id);
        }
        return campaign;
    }

    /** Same as getEntity, but only a campaign the caller may see (see visibleCampaigns). */
    private Campaign getVisibleEntity(UUID id) {
        Campaign campaign = getEntity(id);
        if (!canSee(currentUser(), campaign)) {
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
