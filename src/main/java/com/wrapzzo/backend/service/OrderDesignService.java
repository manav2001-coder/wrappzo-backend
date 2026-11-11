package com.wrapzzo.backend.service;

import com.wrapzzo.backend.security.Permission;
import com.wrapzzo.backend.dto.ArtworkApprovalStep;
import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.OverridableArtworkResponse;
import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.dto.PresignOrderDesignResponse;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.list.ListInMemory;
import com.wrapzzo.backend.list.ListQuery;
import com.wrapzzo.backend.list.PageResponse;
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
import org.springframework.context.ApplicationEventPublisher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
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
    private final ApplicationEventPublisher eventPublisher;

    private static final String PDF_CONTENT_TYPE = "application/pdf";

    // --- Vendor side: only orders assigned to them. ---

    /**
     * Proofs are PDFs, and the vendor must name them PCode_DoctorName_AMName.pdf (the AM's SAP code may stand in for their name): a file
     * named otherwise is refused here, before anything is uploaded, with the name it should have had. A good one is stored under the
     * same name plus its version -- PCODE_Doctor_AM_vN.pdf. Transactional because the name reads the order's lazily-loaded owner (open-in-view is off).
     */
    @Transactional(readOnly = true)
    public PresignOrderDesignResponse presignForVendor(UUID orderId, UUID vendorId, String contentType, String uploadedFileName) {
        String type = contentType == null ? "" : contentType.split(";")[0].trim();
        if (!PDF_CONTENT_TYPE.equalsIgnoreCase(type)) {
            throw new InvalidArtworkFileException("Artwork must be a PDF.");
        }
        Order order = getEntityForVendor(orderId, vendorId);
        NameParts name = nameParts(order);
        if (!ArtworkFileName.matches(uploadedFileName, name.pcode(), name.doctor(), name.amName(), name.amCode())) {
            throw new InvalidArtworkFileException(("\"%s\" is not named the way this order needs. Name the proof PCode_DoctorName_AMName.pdf "
                    + "(the AM's SAP code can be used instead of their name) -- for this order: %s.pdf -- then upload it again.")
                    .formatted(uploadedFileName, ArtworkFileName.example(name.pcode(), name.doctor(), name.amName())));
        }
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
        eventPublisher.publishEvent(new ArtworkAwaitingApprovalEvent(design.getId()));
        return toVendorResponse(design);
    }

    private static String keyPrefix(Order order) {
        return "order-designs/%s/".formatted(order.getId());
    }

    /** What an order's proof is named: the doctor's pcode (the order's own number where there is none, as for a campaign order), the doctor, the AM. */
    private record NameParts(String pcode, String doctor, String amName, String amCode) {
    }

    private NameParts nameParts(Order order) {
        QuickGiftRequest request = quickGiftRequestRepository.findByOrderId(order.getId()).orElse(null);
        String pcode = request != null ? request.getRecipientPcode() : null;
        User owner = order.getOwner();
        return new NameParts(pcode != null && !pcode.isBlank() ? pcode : "ORD-" + order.getOrderNo(), order.getRecipientName(), owner.getName(), owner.getEmployeeCode());
    }

    /** PCODE_Doctor_AM_vN.pdf -- the stored name, the same for every version of the order's proof apart from the number. */
    private String artworkFileName(Order order, int version) {
        NameParts name = nameParts(order);
        return ArtworkFileName.canonical(name.pcode(), name.doctor(), name.amName(), version);
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
                eventPublisher.publishEvent(new ArtworkAwaitingApprovalEvent(design.getId()));
                return toResponse(design);
            }
        } else {
            int level = currentLevel(design);
            List<Stage> chain = approvalChain(design);
            record(design, level, OrderDesignDecision.APPROVED, reviewer, coveredManager(chain, level, reviewer), null, now);
            if (level < levelCount(chain)) {
                moveToLevel(design, level + 1, chain);
                eventPublisher.publishEvent(new ArtworkAwaitingApprovalEvent(design.getId()));
                return toResponse(design);
            }
        }
        design.setStatus(OrderDesignStatus.APPROVED);
        design.setReviewedBy(reviewer);
        design.setReviewedAt(now);
        // Only the final approval tells the vendor; the levels on the way up are the customer's own business.
        eventPublisher.publishEvent(new VendorProofDecidedEvent(design.getId(), true));
        return toResponse(design);
    }

    /**
     * Approves a proof in place of the people below the caller in the approval cycle. Level 1 may cover the MR's own review; level 2
     * covers the MR's review and level 1, and so on up. The caller's one approval counts as theirs and as each step it skips (those are
     * written to the trail as BYPASSED, by them), and the proof moves on to the level above theirs -- or, if theirs was the last, is approved
     * and goes to the vendor. Needs artwork.override as well as a place in this order's cycle above where the proof is waiting.
     */
    @Transactional
    public OrderDesignResponse override(UUID orderId, UUID designId, String comment) {
        OrderDesign design = orderDesignRepository.findById(designId).orElseThrow(() -> new OrderDesignNotFoundException(designId));
        if (!design.getOrder().getId().equals(orderId)) {
            throw new OrderDesignNotFoundException(designId);
        }
        User reviewer = currentUser();
        if (!holds(reviewer, Permission.ARTWORK_OVERRIDE.key())) {
            throw new AccessDeniedException("Approving in place of a lower level needs the Approve in place of lower levels permission, which your role doesn't have");
        }
        if (design.getStatus() != OrderDesignStatus.PENDING_REVIEW && design.getStatus() != OrderDesignStatus.PENDING_FINAL_APPROVAL) {
            throw new OrderDesignAlreadyReviewedException(designId);
        }
        if (reviewer.getCompany() == null || !design.getOrder().getCompany().getId().equals(reviewer.getCompany().getId())) {
            throw new OrderNotFoundException(orderId);
        }
        if (!companyFeatureFlagService.isEnabled(reviewer.getCompany().getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)) {
            throw new AccessDeniedException("Senior artwork approval is not switched on for your company, so there are no levels to approve in place of");
        }
        User requester = requesterOf(design.getOrder());
        if (requester != null && requester.getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("You placed this order, so you can't approve its artwork in place of anyone");
        }
        if (orderDesignApprovalRepository.existsByDesign_IdAndDecidedBy_Id(design.getId(), reviewer.getId())) {
            throw new AccessDeniedException("You have already approved this artwork; the next level needs someone else");
        }
        List<Stage> chain = approvalChain(design);
        int waiting = design.getStatus() == OrderDesignStatus.PENDING_REVIEW ? 0 : currentLevel(design);
        int level = highestLevelAbove(chain, waiting, reviewer);
        if (level == 0) {
            throw new AccessDeniedException("Only someone above the level this artwork is waiting on can approve it in place of the people there. It is waiting for "
                    + waitingLabel(design, chain, waiting));
        }

        Instant now = Instant.now();
        String note = comment == null || comment.isBlank() ? null : comment.trim();
        List<UUID> skippedUsers = new ArrayList<>();
        for (int skipped = waiting; skipped < level; skipped++) {
            List<User> people = skipped == 0 ? (requester == null ? List.of() : List.of(requester)) : peopleAt(chain, skipped);
            User named = people.size() == 1 ? people.get(0) : null;
            record(design, skipped, OrderDesignDecision.BYPASSED, reviewer, named, note, now);
            people.stream().filter(person -> !person.getId().equals(reviewer.getId())).forEach(person -> skippedUsers.add(person.getId()));
        }
        record(design, level, OrderDesignDecision.APPROVED, reviewer, null, note, now);
        if (waiting == 0) {
            design.setFirstApprovedBy(reviewer);
            design.setFirstApprovedAt(now);
        }
        eventPublisher.publishEvent(new ArtworkBypassedEvent(design.getId(), reviewer.getId(), skippedUsers, note));
        if (level < levelCount(chain)) {
            design.setStatus(OrderDesignStatus.PENDING_FINAL_APPROVAL);
            moveToLevel(design, level + 1, chain);
            eventPublisher.publishEvent(new ArtworkAwaitingApprovalEvent(design.getId()));
            return toResponse(design);
        }
        design.setStatus(OrderDesignStatus.APPROVED);
        design.setReviewedBy(reviewer);
        design.setReviewedAt(now);
        eventPublisher.publishEvent(new VendorProofDecidedEvent(design.getId(), true));
        return toResponse(design);
    }

    /**
     * The caller's "Waiting below you" list: proofs still waiting on the MR's review or on a level beneath the caller's own place in
     * the cycle. Needs artwork.override. Bounded by what is waiting in the caller's reach, as the other approval queues are.
     */
    @Transactional(readOnly = true)
    public PageResponse<OverridableArtworkResponse> pageOverridable(ListQuery query) {
        return new ListInMemory<OverridableArtworkResponse>()
                .column("orderDisplayId", OverridableArtworkResponse::orderDisplayId, true)
                .column("giftName", OverridableArtworkResponse::giftName, true)
                .column("recipientName", OverridableArtworkResponse::recipientName, true)
                .column("requestedByName", OverridableArtworkResponse::requestedByName, true)
                .column("quantity", OverridableArtworkResponse::quantity, false)
                .column("versionNo", OverridableArtworkResponse::versionNo, false)
                .column("waitingOn", OverridableArtworkResponse::waitingOn, true)
                .column("waitingLevel", OverridableArtworkResponse::waitingLevel, false)
                .column("waitingSince", OverridableArtworkResponse::waitingSince, false)
                .page(listOverridable(), query, Comparator.comparing(OverridableArtworkResponse::waitingSince, Comparator.nullsLast(Comparator.naturalOrder())));
    }

    private List<OverridableArtworkResponse> listOverridable() {
        User reviewer = currentUser();
        if (reviewer.getCompany() == null || !holds(reviewer, Permission.ARTWORK_OVERRIDE.key())
                || !companyFeatureFlagService.isEnabled(reviewer.getCompany().getId(), CompanyFeature.MARKETING_DESIGN_APPROVAL)) {
            return List.of();
        }
        Visibility visibility = visibilityService.resolve(reviewer);
        List<OrderDesign> waiting = new ArrayList<>();
        for (OrderDesignStatus status : List.of(OrderDesignStatus.PENDING_REVIEW, OrderDesignStatus.PENDING_FINAL_APPROVAL)) {
            waiting.addAll(orderDesignRepository.findByStatusVisible(status, reviewer.getCompany().getId(),
                    visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery()));
        }
        QueueContext context = new QueueContext(reviewer, waiting);
        List<OrderDesign> candidates = new ArrayList<>();
        for (OrderDesign design : waiting) {
            User requester = requesterOf(design.getOrder());
            if ((requester == null || !requester.getId().equals(reviewer.getId())) && !context.decided.contains(design.getId())) {
                candidates.add(design);
            }
        }
        Map<UUID, List<ArtworkApprovalStep>> trails = trailsOf(candidates);
        List<OverridableArtworkResponse> list = new ArrayList<>();
        for (OrderDesign design : candidates) {
            User requester = requesterOf(design.getOrder());
            List<Stage> chain = context.chain(design);
            int waitingLevel = design.getStatus() == OrderDesignStatus.PENDING_REVIEW ? 0 : currentLevel(design);
            int level = highestLevelAbove(chain, waitingLevel, reviewer);
            if (level == 0) {
                continue;
            }
            List<String> skipping = new ArrayList<>();
            for (int skipped = waitingLevel; skipped < level; skipped++) {
                skipping.add(skipped == 0
                        ? "MR review" + (requester != null ? " (" + requester.getName() + ")" : "")
                        : "Level " + skipped + " (" + chain.get(skipped - 1).label() + ")");
            }
            list.add(OverridableArtworkResponse.from(design, fileStorageService.publicUrl(design.getFileKey()), requester != null ? requester.getName() : null,
                    waitingLabel(design, chain, waitingLevel), waitingLevel, level, levelCount(chain), skipping, trails.getOrDefault(design.getId(), List.of())));
        }
        return list;
    }

    /** The highest level above {@code waiting} whose people include the reviewer, or 0 if there is none: the level their approval counts as. */
    private static int highestLevelAbove(List<Stage> chain, int waiting, User reviewer) {
        for (int level = chain.size(); level > waiting; level--) {
            if (chain.get(level - 1).includes(reviewer)) {
                return level;
            }
        }
        return 0;
    }

    private static List<User> peopleAt(List<Stage> chain, int level) {
        Stage stage = expectedApprover(chain, level);
        return stage == null ? List.of() : stage.named() != null ? List.of(stage.named()) : stage.eligible();
    }

    private String waitingLabel(OrderDesign design, List<Stage> chain, int waiting) {
        if (waiting == 0) {
            User requester = requesterOf(design.getOrder());
            return "the MR's review" + (requester != null ? " (" + requester.getName() + ")" : "");
        }
        Stage stage = expectedApprover(chain, waiting);
        return "level " + waiting + (stage != null ? " (" + stage.label() + ")" : "");
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
        eventPublisher.publishEvent(new VendorProofDecidedEvent(design.getId(), false));
        return toResponse(design);
    }

    /**
     * A manager's queue: MR-approved proofs whose current level waits on them -- or, for an
     * order.design.final holder, every one they haven't already signed (covering for an absent
     * manager, or for an MR with no one above them).
     */
    @Transactional(readOnly = true)
    private List<PendingArtworkApprovalResponse> listPendingFinalApproval() {
        User reviewer = currentUser();
        if (reviewer.getCompany() == null) {
            return List.of();
        }
        // Only proofs of orders in the reviewer's reach can be theirs (the manager a level waits on sees their team's orders; cover is gated on seeing the order).
        Visibility visibility = visibilityService.resolve(reviewer);
        List<OrderDesign> pending = orderDesignRepository.findByStatusVisible(OrderDesignStatus.PENDING_FINAL_APPROVAL, reviewer.getCompany().getId(),
                visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery());
        QueueContext context = new QueueContext(reviewer, pending);
        List<OrderDesign> mine = pending.stream().filter(design -> canApproveCurrentLevel(design, reviewer, context)).toList();
        Map<UUID, List<ArtworkApprovalStep>> trails = trailsOf(mine);
        List<PendingArtworkApprovalResponse> queue = new ArrayList<>();
        for (OrderDesign design : mine) {
            Stage stage = expectedApprover(context.chain(design), currentLevel(design));
            boolean covering = stage == null || !stage.includes(reviewer);
            queue.add(PendingArtworkApprovalResponse.from(design, fileStorageService.publicUrl(design.getFileKey()),
                    requesterOf(design.getOrder()), stage != null ? stage.label() : null, covering,
                    trails.getOrDefault(design.getId(), List.of())));
        }
        return queue;
    }

    /** One page of the proofs waiting on the caller, searched, filtered and sorted. Bounded by what is waiting on them, not by all there ever was. */
    @Transactional(readOnly = true)
    public PageResponse<PendingArtworkApprovalResponse> pagePendingFinalApproval(ListQuery query) {
        return new ListInMemory<PendingArtworkApprovalResponse>()
                .column("orderDisplayId", PendingArtworkApprovalResponse::orderDisplayId, true)
                .column("giftName", PendingArtworkApprovalResponse::giftName, true)
                .column("recipientName", PendingArtworkApprovalResponse::recipientName, true)
                .column("requestedByName", PendingArtworkApprovalResponse::requestedByName, true)
                .column("quantity", PendingArtworkApprovalResponse::quantity, false)
                .column("versionNo", PendingArtworkApprovalResponse::versionNo, false)
                .column("approvalLevel", PendingArtworkApprovalResponse::approvalLevel, false)
                .column("mrApprovedAt", PendingArtworkApprovalResponse::mrApprovedAt, false)
                .page(listPendingFinalApproval(), query, Comparator.comparing(PendingArtworkApprovalResponse::mrApprovedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed());
    }

    /**
     * Who a proof waits on right now: the MR who placed the order for the first review, then the people
     * the current level names (never anyone who has already signed it, nor the MR on their own proof).
     * Active users with an email only -- these are the people a reminder goes to.
     */
    @Transactional(readOnly = true)
    public List<User> awaitedApprovers(OrderDesign design) {
        List<User> candidates;
        if (design.getStatus() == OrderDesignStatus.PENDING_REVIEW) {
            User owner = requesterOf(design.getOrder());
            candidates = owner == null ? List.of() : List.of(owner);
        } else if (design.getStatus() == OrderDesignStatus.PENDING_FINAL_APPROVAL) {
            Stage stage = expectedApprover(approvalChain(design), currentLevel(design));
            User requester = requesterOf(design.getOrder());
            candidates = stage == null ? List.of() : stage.eligible().stream()
                    .filter(user -> requester == null || !user.getId().equals(requester.getId()))
                    .filter(user -> !orderDesignApprovalRepository.existsByDesign_IdAndDecidedBy_Id(design.getId(), user.getId()))
                    .toList();
        } else {
            return List.of();
        }
        return candidates.stream()
                .filter(user -> user.getStatus() == UserStatus.ACTIVE && user.getEmail() != null && !user.getEmail().isBlank())
                .toList();
    }

    private OrderDesign reviewableDesign(UUID orderId, UUID designId) {
        OrderDesign design = orderDesignRepository.findById(designId).orElseThrow(() -> new OrderDesignNotFoundException(designId));
        if (!design.getOrder().getId().equals(orderId)) {
            throw new OrderDesignNotFoundException(designId);
        }
        switch (design.getStatus()) {
            case PENDING_REVIEW -> {
                // The MR's own first review (the green flag on the Orders page): artwork.review, nothing else.
                if (!holds(currentUser(), Permission.ARTWORK_REVIEW.key())) {
                    throw new AccessDeniedException("Reviewing a proof needs the Review artwork permission, which your role doesn't have");
                }
                getOwnCompanyEntity(orderId);
            }
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
                    if (stage != null && stage.includes(reviewer)) {
                        throw new AccessDeniedException("This artwork is waiting for you, but your role doesn't have the Approve artwork permission");
                    }
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
        return canApproveCurrentLevel(design, reviewer, null);
    }

    /**
     * What a queue computes once for the whole list rather than once per proof: the reviewer's permission keys and visibility (each a cache
     * or database read), which proofs they have already signed (one query), and the approval chain of each requester (it depends on who placed
     * the order, not on the proof). A list of thousands of proofs was doing all of these thousands of times.
     */
    private final class QueueContext {
        final User reviewer;
        final Set<String> keys;
        final Visibility visibility;
        final Set<UUID> decided;
        private final Map<UUID, List<Stage>> chains = new HashMap<>();

        QueueContext(User reviewer, List<OrderDesign> designs) {
            this.reviewer = reviewer;
            this.keys = accessControlCacheService.resolveEffectiveKeys(reviewer);
            this.visibility = visibilityService.resolve(reviewer);
            this.decided = designs.isEmpty() ? Set.of()
                    : orderDesignApprovalRepository.findDesignIdsDecidedBy(designs.stream().map(OrderDesign::getId).toList(), reviewer.getId());
        }

        List<Stage> chain(OrderDesign design) {
            User requester = requesterOf(design.getOrder());
            return chains.computeIfAbsent(requester == null ? new UUID(0, 0) : requester.getId(), id -> approvalChain(design));
        }
    }

    private boolean canApproveCurrentLevel(OrderDesign design, User reviewer, QueueContext context) {
        User requester = requesterOf(design.getOrder());
        if (requester != null && requester.getId().equals(reviewer.getId())) {
            return false;
        }
        boolean alreadySigned = context != null ? context.decided.contains(design.getId())
                : orderDesignApprovalRepository.existsByDesign_IdAndDecidedBy_Id(design.getId(), reviewer.getId());
        if (alreadySigned) {
            return false;
        }
        Stage stage = expectedApprover(context != null ? context.chain(design) : approvalChain(design), currentLevel(design));
        Set<String> keys = context != null ? context.keys : accessControlCacheService.resolveEffectiveKeys(reviewer);
        if (stage != null && stage.includes(reviewer) && keys.contains(Permission.APPROVAL_DECIDE.key())) {
            return true;
        }
        Order order = design.getOrder();
        Visibility visibility = context != null ? context.visibility : null;
        return keys.contains(FINAL_APPROVAL_KEY)
                && (visibility != null ? visibility : visibilityService.resolve(reviewer)).canSee(order.getOwner(), order.getOrgUnit());
    }

    private boolean holds(User user, String key) {
        return accessControlCacheService.resolveEffectiveKeys(user).contains(key);
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
        design.startAwaiting(Instant.now());
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
