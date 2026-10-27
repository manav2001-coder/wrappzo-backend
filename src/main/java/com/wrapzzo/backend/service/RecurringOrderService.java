package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.QuickGiftRequestResponse;
import com.wrapzzo.backend.dto.RecurringOrderPlanResponse;
import com.wrapzzo.backend.exception.QuickGiftRequestNotFoundException;
import com.wrapzzo.backend.exception.RecurringOrderPlanNotFoundException;
import com.wrapzzo.backend.model.QuickGiftRequest;
import com.wrapzzo.backend.model.RecurringOrderPlan;
import com.wrapzzo.backend.model.RecurringPlanStatus;
import com.wrapzzo.backend.repository.QuickGiftRequestRepository;
import com.wrapzzo.backend.repository.RecipientRepository;
import com.wrapzzo.backend.repository.RecurringOrderPlanRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Recurring orders: a Quick Gift repeated every month. Making a plan places the first order
 * straight away; each later one is placed by RecurringOrderJob (or the requester's "place the
 * next one now") through QuickGiftService.create, so it gets the same roster check, approval or
 * direct-to-vendor routing, and warehouse routing as a manual request. Plans are the requester's
 * own -- nobody else lists or changes them.
 */
@Service
@RequiredArgsConstructor
public class RecurringOrderService {

    private final RecurringOrderPlanRepository planRepository;
    private final QuickGiftService quickGiftService;
    private final QuickGiftRequestRepository quickGiftRequestRepository;
    private final RecipientRepository recipientRepository;

    /** The first order now, then one every month from today. */
    @Transactional
    public RecurringOrderPlanResponse create(CreateQuickGiftRequest request) {
        QuickGiftRequestResponse first = quickGiftService.create(request);
        QuickGiftRequest placed = quickGiftRequestRepository.findById(first.id())
                .orElseThrow(() -> new QuickGiftRequestNotFoundException(first.id()));

        RecurringOrderPlan plan = new RecurringOrderPlan();
        plan.setCompany(placed.getCompany());
        plan.setRequestedBy(placed.getRequestedBy());
        plan.setGift(placed.getGift());
        plan.setRecipient(request.recipientId() != null ? recipientRepository.findById(request.recipientId()).orElse(null) : null);
        plan.setRecipientName(placed.getRecipientName());
        plan.setRecipientPhone(placed.getRecipientPhone());
        plan.setRecipientEmail(placed.getRecipientEmail());
        plan.setDeliveryAddress(placed.getDeliveryAddress());
        plan.setRecipientQualification(placed.getRecipientQualification());
        plan.setRecipientRegistrationNumber(placed.getRecipientRegistrationNumber());
        plan.setRecipientSpeciality(placed.getRecipientSpeciality());
        plan.setRecipientClinicName(placed.getRecipientClinicName());
        plan.setGiftMessage(placed.getGiftMessage());
        plan.setQuantity(placed.getQuantity());
        plan.setNextRunOn(LocalDate.now().plusMonths(1));
        plan.setRunsCompleted(1);
        return RecurringOrderPlanResponse.from(planRepository.save(plan));
    }

    @Transactional(readOnly = true)
    public List<RecurringOrderPlanResponse> listMine() {
        return planRepository.findByRequestedByIdOrderByCreatedAtDesc(CurrentUser.id()).stream()
                .map(RecurringOrderPlanResponse::from)
                .toList();
    }

    @Transactional
    public RecurringOrderPlanResponse pause(UUID id) {
        RecurringOrderPlan plan = getOwnLivePlan(id);
        plan.setStatus(RecurringPlanStatus.PAUSED);
        return RecurringOrderPlanResponse.from(plan);
    }

    /** Back on; a run date that slipped by while paused moves to today, so it is placed by the next job run, not skipped. */
    @Transactional
    public RecurringOrderPlanResponse resume(UUID id) {
        RecurringOrderPlan plan = getOwnLivePlan(id);
        plan.setStatus(RecurringPlanStatus.ACTIVE);
        if (plan.getNextRunOn().isBefore(LocalDate.now())) {
            plan.setNextRunOn(LocalDate.now());
        }
        return RecurringOrderPlanResponse.from(plan);
    }

    @Transactional
    public RecurringOrderPlanResponse cancel(UUID id) {
        RecurringOrderPlan plan = getOwnLivePlan(id);
        plan.setStatus(RecurringPlanStatus.CANCELLED);
        return RecurringOrderPlanResponse.from(plan);
    }

    /** The order that was due next, placed now; the one after it is a month after the date it replaces. */
    @Transactional
    public RecurringOrderPlanResponse placeNextNow(UUID id) {
        RecurringOrderPlan plan = getOwnLivePlan(id);
        placeAndAdvance(plan);
        return RecurringOrderPlanResponse.from(plan);
    }

    /** Plans due today or earlier -- RecurringOrderJob places each in its own transaction. */
    @Transactional(readOnly = true)
    public List<DuePlan> findDue(LocalDate today) {
        return planRepository.findByStatusAndNextRunOnLessThanEqual(RecurringPlanStatus.ACTIVE, today).stream()
                .map(p -> new DuePlan(p.getId(), p.getRequestedBy().getId()))
                .toList();
    }

    /** One scheduled run, as the plan's requester (the job sets them as the current user). */
    @Transactional
    public void placeDue(UUID planId) {
        RecurringOrderPlan plan = planRepository.findById(planId).orElseThrow(() -> new RecurringOrderPlanNotFoundException(planId));
        if (plan.getStatus() == RecurringPlanStatus.ACTIVE && !plan.getNextRunOn().isAfter(LocalDate.now())) {
            placeAndAdvance(plan);
        }
    }

    /** A failed run stays due, so tomorrow's job tries it again; the requester sees why on the plan. */
    @Transactional
    public void recordFailure(UUID planId, String reason) {
        planRepository.findById(planId).ifPresent(plan -> plan.setLastError(reason));
    }

    private void placeAndAdvance(RecurringOrderPlan plan) {
        UUID recipientId = plan.getRecipient() != null ? plan.getRecipient().getId() : null;
        quickGiftService.create(new CreateQuickGiftRequest(
                plan.getGift().getId(), recipientId, plan.getRecipientName(), plan.getRecipientPhone(), plan.getRecipientEmail(),
                plan.getDeliveryAddress(), null, plan.getGiftMessage(), "Recurring order", null,
                new DoctorDetails(plan.getRecipientQualification(), plan.getRecipientRegistrationNumber(),
                        plan.getRecipientSpeciality(), plan.getRecipientClinicName()),
                plan.getQuantity()));
        LocalDate next = plan.getNextRunOn().plusMonths(1);
        // Never queue a backlog -- after downtime, the next run is a month from today, not a string of catch-up orders.
        plan.setNextRunOn(next.isAfter(LocalDate.now()) ? next : LocalDate.now().plusMonths(1));
        plan.setRunsCompleted(plan.getRunsCompleted() + 1);
        plan.setLastError(null);
    }

    /** 404 for another user's plan and for a cancelled one alike. */
    private RecurringOrderPlan getOwnLivePlan(UUID id) {
        RecurringOrderPlan plan = planRepository.findById(id).orElseThrow(() -> new RecurringOrderPlanNotFoundException(id));
        if (!plan.getRequestedBy().getId().equals(CurrentUser.id()) || plan.getStatus() == RecurringPlanStatus.CANCELLED) {
            throw new RecurringOrderPlanNotFoundException(id);
        }
        return plan;
    }

    public record DuePlan(UUID planId, UUID requesterId) {
    }
}
