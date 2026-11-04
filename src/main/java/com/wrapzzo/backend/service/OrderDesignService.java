package com.wrapzzo.backend.service;

import com.wrapzzo.backend.security.Permission;
import com.wrapzzo.backend.dto.ArtworkApprovalStep;
import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.dto.PresignOrderDesignResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.InvalidArtworkFileException;
import com.wrapzzo.backend.exception.OrderDesignAlreadyReviewedException;
import com.wrapzzo.backend.exception.OrderDesignNotFoundException;
import com.wrapzzo.backend.exception.OrderNotFoundException;
import com.wrapzzo.backend.model.ArtworkApprovalScope;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyArtworkApprovalStep;
import com.wrapzzo.backend.model.CompanyFeature;
import com.wrapzzo.backend.model.Order;
import com.wrapzzo.backend.model.OrderDesign;
import com.wrapzzo.backend.model.OrderDesignApproval;
import com.wrapzzo.backend.model.OrderDesignDecision;
import com.wrapzzo.backend.model.OrderDesignStatus;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyArtworkApprovalStepRepository;
import com.wrapzzo.backend.repository.OrderDesignApprovalRepository;
import com.wrapzzo.backend.repository.OrderDesignRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.OrderRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Artwork proof review loop for an order (PEL process steps 5-6: Design &amp; QC, then
 * Review &amp; rework) -- the vendor assigned to the order uploads a version, a company
 * reviewer (order.design.review) approves it or rejects it with a comment, which the
 * vendor sees and answers with the next version. Deliberately its own service rather than
 * folded into OrderService: a distinct actor pair (vendor uploads, company reviews) and a
 * distinct access-control key on each side.
 */
@Service
@RequiredArgsConstructor
public class OrderDesignService {

    /** Granted to Company Admin by default (V42): sign any management level on artwork, as cover for the manager it waits on. */
    public static final String FINAL_APPROVAL_KEY = Permission.ARTWORK_FINAL_APPROVE.key();

    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private final OrderDesignRepository orderDesignRepository;
    private final OrderDesignApprovalRepository orderDesignApprovalRepository;
    private final OrderRepository orderRepository;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final CompanyArtworkApprovalStepRepository approvalStepRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final VendorRepository vendorRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;
    private final VisibilityService visibilityService;
    private final CompanyFeatureFlagService companyFeatureFlagService;
    private final AccessControlCacheService accessControlCacheService;

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    // --- Vendor side: only orders assigned to them. ---

    /**
     * Proofs are PDFs, named PCODE_AMCODE_vN.pdf: the doctor's pcode, the ordering AM's code (their name if they have none), the version.
     * Transactional because the name reads the order's lazily-loaded owner (open-in-view is off).
     */
    @Transactional(readOnly = true)
    public PresignOrderDesignResponse presignForVendor(UUID orderId, UUID vendorId, String contentType) {
        String type = contentType == null ? "" : contentType.split(";")[0].trim();
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(type)) {
            throw new InvalidArtworkFileException("Artwork must be a PDF.");
        }
        Order order = getEntityForVendor(orderId, vendorId);
        String fileName = artworkFileName(order, orderDesignRepository.countByOrder_Id(order.getId()) + 1);
        String key = "%s%s/%s".formatted(keyPrefix(order), UUID.randomUUID(), fileName);
        String uploadUrl = fileStorageService.presignUpload(key, PDF_CONTENT_TYPE, UPLOAD_URL_EXPIRY);
        return new PresignOrderDesignResponse(uploadUrl, key, fileName);
    }

    /** The file name is the server's, never the vendor's: it is the last part of the key this order's presign handed out. */
    @Transactional
    public OrderDesignResponse uploadForVendor(UUID orderId, UUID vendorId, String fileKey) {
        Order order = getEntityForVendor(orderId, vendorId);
        Vendor vendor = vendorRepository.findById(vendorId).orElseThrow(() -> new OrderNotFoundException(orderId));
        int nextVersion = orderDesignRepository.countByOrder_Id(order.getId()) + 1;
        String fileName = fileKey.substring(fileKey.lastIndexOf('/') + 1);
        if (!fileKey.startsWith(keyPrefix(order)) || !fileName.toLowerCase().endsWith(".pdf")) {
            throw new InvalidArtworkFileException("That is not a PDF uploaded for this order.");
        }
        if (!fileName.equals(artworkFileName(order, nextVersion))) {
            throw new ConflictException("Another version was uploaded in the meantime -- upload this one again.");
        }
        OrderDesign design = orderDesignRepository.save(new OrderDesign(order, nextVersion, fileKey, fileName, vendor));
        return toVendorResponse(design);
    }

    private static String keyPrefix(Order order) {
        return "order-designs/%s/".formatted(order.getId());
    }

    /** PCODE_AMCODE_vN.pdf -- falls back to the order's own id where there is no doctor pcode (a campaign order). */
    private String artworkFileName(Order order, int version) {
        QuickGiftRequest request = quickGiftRequestRepository.findByOrderId(order.getId()).orElse(null);
        String pcode = request != null ? request.getRecipientPcode() : null;
        User am = order.getOwner();
        String amCode = am.getEmployeeCode() != null && !am.getEmployeeCode().isBlank() ? am.getEmployeeCode() : am.getName();
        return "%s_%s_v%d.pdf".formatted(
                fileNamePart(pcode != null && !pcode.isBlank() ? pcode : "ORD-" + order.getOrderNo()), fileNamePart(amCode), version);
    }

    /** Letters and digits only, runs of anything else collapsed to one hyphen: safe in a key, a URL and a download. */
    private static String fileNamePart(String value) {
        String part = value.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return part.isEmpty() ? "NA" : part;
    }

    @Transactional(readOnly = true)
    public List<OrderDesignResponse> listForVendor(UUID orderId, UUID vendorId) {
        Order order = getEntityForVendor(orderId, vendorId);
        return orderDesignRepository.findByOrder_IdOrderByVersionNoDesc(order.getId()).stream().map(this::toVendorResponse).toList();
    }

    // --- Company side: only orders in the reviewer's own company. ---

    @Transactional(readOnly = true)
    public List<OrderDesignResponse> listForCompany(UUID orderId) {
        Order order = getOwnCompanyEntity(orderId);
        List<OrderDesign> designs = orderDesignRepository.findByOrder_IdOrderByVersionNoDesc(order.getId());
        Map<UUID, List<ArtworkApprovalStep>> trails = trailsOf(designs);
        return designs.stream().map(design -> toResponse(design, trails.getOrDefault(design.getId(), List.of()))).toList();
    }

    /**
     * The MR's approval, then one manager's per level. With the company's MARKETING_DESIGN_APPROVAL
     * flag on, an MR-approved proof climbs the MR's reporting line (PENDING_FINAL_APPROVAL, level 1
     * = their manager) up to the company's artwork_approval_levels, or the top of the line if that
     * comes first; the last level's approval is final. Only APPROVED unlocks dispatch (OrderService's
     * artwork gate).
     */
    @Transactional
    public OrderDesignResponse approve(UUID orderId, UUID designId) {
        OrderDesign design = reviewableDesign(orderId, designId);
        User reviewer = currentUser();
        Instant now = Instant.now();
        if (design.getStatus() == OrderDesignStatus.PENDING_REVIEW) {
            record(design, 0, OrderDesignDecision.APPROVED, reviewer, null, null, now);
            if (companyFeatureFlagService.isEnabled(design.getOrder().getCompany().getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)) {
                design.setStatus(OrderDesignStatus.PENDING_FINAL_APPROVAL);
                design.setFirstApprovedBy(reviewer);
                design.setFirstApprovedAt(now);
                moveToLevel(design, 1, approvalChain(design));
                return toResponse(design);
            }
        } else {
            int level = currentLevel(design);
            List<Stage> chain = approvalChain(design);
            record(design, level, OrderDesignDecision.APPROVED, reviewer, coveredManager(chain, level, reviewer), null, now);
            if (level < levelCount(chain)) {
                moveToLevel(design, level + 1, chain);
                return toResponse(design);
            }
        }
        design.setStatus(OrderDesignStatus.APPROVED);
        design.setReviewedBy(reviewer);
        design.setReviewedAt(now);
        return toResponse(design);
    }

    /** Sends the proof back to the vendor -- from the MR's review or from any management level. */
    @Transactional
    public OrderDesignResponse reject(UUID orderId, UUID designId, String comment) {
        OrderDesign design = reviewableDesign(orderId, designId);
        User reviewer = currentUser();
        Instant now = Instant.now();
        if (design.getStatus() == OrderDesignStatus.PENDING_REVIEW) {
            record(design, 0, OrderDesignDecision.REJECTED, reviewer, null, comment, now);
        } else {
            int level = currentLevel(design);
            record(design, level, OrderDesignDecision.REJECTED, reviewer, coveredManager(approvalChain(design), level, reviewer), comment, now);
        }
        design.setStatus(OrderDesignStatus.REJECTED);
        design.setReviewComment(comment);
        design.setReviewedBy(reviewer);
        design.setReviewedAt(now);
        return toResponse(design);
    }

    /**
     * A manager's queue: MR-approved proofs whose current level waits on them -- or, for an
     * order.design.final holder, every one they haven't already signed (covering for an absent
     * manager, or for an MR with no one above them).
     */
    @Transactional(readOnly = true)
    public List<PendingArtworkApprovalResponse> listPendingFinalApproval() {
        User reviewer = currentUser();
        if (reviewer.getCompany() == null) {
            return List.of();
        }
        List<OrderDesign> pending = orderDesignRepository
                .findByStatusAndOrder_Company_IdOrderByUpdatedAtAsc(OrderDesignStatus.PENDING_FINAL_APPROVAL, reviewer.getCompany().getId());
        Map<UUID, List<ArtworkApprovalStep>> trails = trailsOf(pending);
        List<PendingArtworkApprovalResponse> queue = new ArrayList<>();
        for (OrderDesign design : pending) {
            if (!canApproveCurrentLevel(design, reviewer)) {
                continue;
            }
            Stage stage = expectedApprover(approvalChain(design), currentLevel(design));
            boolean covering = stage == null || !stage.includes(reviewer);
            queue.add(PendingArtworkApprovalResponse.from(design, fileStorageService.publicUrl(design.getFileKey()),
                    requesterOf(design.getOrder()), stage != null ? stage.label() : null, covering,
                    trails.getOrDefault(design.getId(), List.of())));
        }
        return queue;
    }

    private OrderDesign reviewableDesign(UUID orderId, UUID designId) {
        OrderDesign design = orderDesignRepository.findById(designId).orElseThrow(() -> new OrderDesignNotFoundException(designId));
        if (!design.getOrder().getId().equals(orderId)) {
            throw new OrderDesignNotFoundException(designId);
        }
        switch (design.getStatus()) {
            case PENDING_REVIEW -> getOwnCompanyEntity(orderId);
            case PENDING_FINAL_APPROVAL -> {
                // Authorised by the approval chain (canApproveCurrentLevel): the manager the level
                // waits on is above the MR, so always sees the order; a cover signer must see it too.
                User reviewer = currentUser();
                if (reviewer.getCompany() == null || !design.getOrder().getCompany().getId().equals(reviewer.getCompany().getId())) {
                    throw new OrderNotFoundException(orderId);
                }
                if (orderDesignApprovalRepository.existsByDesign_IdAndDecidedBy_Id(design.getId(), reviewer.getId())) {
                    throw new AccessDeniedException("You have already approved this artwork; the next level needs someone else");
                }
                if (!canApproveCurrentLevel(design, reviewer)) {
                    Stage stage = expectedApprover(approvalChain(design), currentLevel(design));
                    throw new AccessDeniedException(stage != null
                            ? "This artwork is waiting for approval from " + stage.label()
                            : "Only a Final Artwork Approval holder can approve this artwork");
                }
            }
            default -> throw new OrderDesignAlreadyReviewedException(designId);
        }
        return design;
    }

    /**
     * The manager the current level waits on may sign it; so may an order.artwork_final holder who
     * can see the order (see Visibility), as cover. Nobody signs twice on one proof (the MR's own
     * approval included), and the MR who placed the order never signs their own artwork up the line.
     */
    private boolean canApproveCurrentLevel(OrderDesign design, User reviewer) {
        User requester = requesterOf(design.getOrder());
        if (requester != null && requester.getId().equals(reviewer.getId())) {
            return false;
        }
        if (orderDesignApprovalRepository.existsByDesign_IdAndDecidedBy_Id(design.getId(), reviewer.getId())) {
            return false;
        }
        Stage stage = expectedApprover(approvalChain(design), currentLevel(design));
        if (stage != null && stage.includes(reviewer)) {
            return true;
        }
        Order order = design.getOrder();
        return accessControlCacheService.resolveEffectiveKeys(reviewer).contains(FINAL_APPROVAL_KEY)
                && visibilityService.resolve(reviewer).canSee(order.getOwner(), order.getOrgUnit());
    }

    /** One level of the chain: who may sign it, and how to name them (a person, or "HO of Summit"). */
    private record Stage(User named, List<User> eligible, String label) {
        boolean includes(User user) {
            return eligible.stream().anyMatch(candidate -> candidate.getId().equals(user.getId()));
        }
    }

    /**
     * The levels after the MR's own review, resolved fresh at every step so a change in the reporting line, a
     * placement or the company's steps applies to the next level straight away. A company with approval steps
     * (V60) follows them -- each a role found in the MR's reporting line or in their division; one without keeps
     * the older "the managers above the MR, nearest first, at most artwork_approval_levels of them".
     */
    private List<Stage> approvalChain(OrderDesign design) {
        Company company = design.getOrder().getCompany();
        User requester = requesterOf(design.getOrder());
        if (requester == null) {
            return List.of();
        }
        List<CompanyArtworkApprovalStep> steps = approvalStepRepository.findByCompany_IdOrderByStepNoAsc(company.getId());
        if (steps.isEmpty()) {
            return managersUpTheLine(requester, company.getArtworkApprovalLevels());
        }
        return steps.stream().map(step -> stageOf(step, requester)).toList();
    }

    private static List<Stage> managersUpTheLine(User requester, int maxLevels) {
        List<Stage> chain = new ArrayList<>();
        Set<UUID> seen = new HashSet<>(List.of(requester.getId()));
        User next = requester.getReportsTo();
        while (next != null && chain.size() < maxLevels && seen.add(next.getId())) {
            chain.add(new Stage(next, List.of(next), next.getName()));
            next = next.getReportsTo();
        }
        return chain;
    }

    private Stage stageOf(CompanyArtworkApprovalStep step, User requester) {
        UUID roleId = step.getRole().getId();
        String roleName = step.getRole().getName();
        if (step.getScope() == ArtworkApprovalScope.REPORTING_LINE) {
            Set<UUID> seen = new HashSet<>(List.of(requester.getId()));
            for (User manager = requester.getReportsTo(); manager != null && seen.add(manager.getId()); manager = manager.getReportsTo()) {
                if (manager.getRole().getId().equals(roleId)) {
                    return new Stage(manager, List.of(manager), manager.getName());
                }
            }
            return new Stage(null, List.of(), roleName);
        }
        OrgUnit division = divisionOf(requester);
        List<User> holders = division == null
                ? List.of()
                : userRepository.findByCompanyIdAndRoleId(requester.getCompany().getId(), roleId).stream()
                        .filter(user -> user.getStatus() == UserStatus.ACTIVE && coversDivision(user, division))
                        .toList();
        String label = roleName + (division != null ? " of " + division.getName() : "");
        return new Stage(holders.size() == 1 ? holders.get(0) : null, holders, label);
    }

    /** The top of the MR's org tree -- level 1, the division -- or null for someone placed nowhere. */
    private OrgUnit divisionOf(User user) {
        OrgUnit unit = user.getOrgUnit();
        if (unit == null) {
            return null;
        }
        return unit.getAncestorIds().isEmpty() ? unit : orgUnitRepository.findById(unit.getAncestorIds().get(0)).orElse(unit);
    }

    /** Placed in the division itself, or given it as an extra unit to cover. */
    private static boolean coversDivision(User user, OrgUnit division) {
        return (user.getOrgUnit() != null && user.getOrgUnit().getId().equals(division.getId()))
                || user.getExtraOrgUnits().stream().anyMatch(unit -> unit.getId().equals(division.getId()));
    }

    /** Levels this proof climbs: one per stage, and at least one -- with no one above the MR, a Final Artwork Approval holder signs it. */
    private static int levelCount(List<Stage> chain) {
        return Math.max(1, chain.size());
    }

    private static Stage expectedApprover(List<Stage> chain, int level) {
        return level >= 1 && level <= chain.size() ? chain.get(level - 1) : null;
    }

    /** The manager a level was waiting on, when someone else signs it for them; null when they sign it themselves or no one person was meant. */
    private static User coveredManager(List<Stage> chain, int level, User reviewer) {
        Stage stage = expectedApprover(chain, level);
        return stage != null && stage.named() != null && !stage.named().getId().equals(reviewer.getId()) && !stage.includes(reviewer)
                ? stage.named()
                : null;
    }

    private static int currentLevel(OrderDesign design) {
        return design.getApprovalLevel() != null ? design.getApprovalLevel() : 1;
    }

    private static void moveToLevel(OrderDesign design, int level, List<Stage> chain) {
        design.setApprovalLevel(level);
        design.setApprovalLevels(levelCount(chain));
    }

    private void record(OrderDesign design, int level, OrderDesignDecision decision, User reviewer, User onBehalfOf, String comment, Instant at) {
        orderDesignApprovalRepository.save(new OrderDesignApproval(design, level, decision, reviewer, onBehalfOf, comment, at));
    }

    private Map<UUID, List<ArtworkApprovalStep>> trailsOf(List<OrderDesign> designs) {
        if (designs.isEmpty()) {
            return Map.of();
        }
        return orderDesignApprovalRepository.findByDesign_IdInOrderByDecidedAtAsc(designs.stream().map(OrderDesign::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(approval -> approval.getDesign().getId(),
                        Collectors.mapping(ArtworkApprovalStep::from, Collectors.toList())));
    }

    /** The MR behind an order -- its owner, the person it was placed for (see Order.owner). */
    private static User requesterOf(Order order) {
        return order.getOwner();
    }

    private Order getEntityForVendor(UUID orderId, UUID vendorId) {
        Order order = getEntity(orderId);
        if (order.getAssignedVendor() == null || !order.getAssignedVendor().getId().equals(vendorId)) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    /**
     * A reviewer reviews only orders they can see (the MR who placed it always can) -- the same
     * Visibility the Orders list uses, so "can see it" and "can review it" never disagree.
     */
    private Order getOwnCompanyEntity(UUID orderId) {
        Order order = getEntity(orderId);
        User user = currentUser();
        if (!order.getCompany().getId().equals(user.getCompany().getId())
                || !visibilityService.resolve(user).canSee(order.getOwner(), order.getOrgUnit())) {
            throw new OrderNotFoundException(orderId);
        }
        return order;
    }

    private Order getEntity(UUID orderId) {
        return orderRepository.findById(orderId).orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    /** Company side: with the approval trail and, while it climbs, who the current level waits on. */
    private OrderDesignResponse toResponse(OrderDesign design, List<ArtworkApprovalStep> trail) {
        String waitingOn = null;
        if (design.getStatus() == OrderDesignStatus.PENDING_FINAL_APPROVAL) {
            Stage stage = expectedApprover(approvalChain(design), currentLevel(design));
            waitingOn = stage != null ? stage.label() : null;
        }
        return OrderDesignResponse.from(design, fileStorageService.publicUrl(design.getFileKey()), trail, waitingOn);
    }

    private OrderDesignResponse toResponse(OrderDesign design) {
        return toResponse(design, trailsOf(List.of(design)).getOrDefault(design.getId(), List.of()));
    }

    /** Vendor side: the level numbers, but not the names inside the customer's hierarchy. */
    private OrderDesignResponse toVendorResponse(OrderDesign design) {
        return OrderDesignResponse.from(design, fileStorageService.publicUrl(design.getFileKey()), List.of(), null);
    }
}
