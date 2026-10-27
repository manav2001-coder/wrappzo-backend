package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateDoctorChangeRequest;
import com.wrapzzo.backend.dto.DoctorChangeRequestResponse;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.exception.DoctorChangeRequestAlreadyReviewedException;
import com.wrapzzo.backend.exception.DoctorChangeRequestNotFoundException;
import com.wrapzzo.backend.exception.RecipientAlreadyExistsException;
import com.wrapzzo.backend.exception.RecipientNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.DoctorChangeAction;
import com.wrapzzo.backend.model.DoctorChangeRequest;
import com.wrapzzo.backend.model.DoctorChangeStatus;
import com.wrapzzo.backend.model.Recipient;
import com.wrapzzo.backend.model.RecipientAssignment;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.DoctorChangeRequestRepository;
import com.wrapzzo.backend.repository.RecipientAssignmentRepository;
import com.wrapzzo.backend.repository.RecipientRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * MR doctor-change requests (PRD: add/edit/remove, routed to the MR's own manager for
 * approval before taking effect). Approval authority is the requester's direct manager
 * (User.reportsTo) -- a reporting-chain check, not a flat access-control key -- plus
 * recipient.approve as a company-wide escalation path for when a manager isn't available.
 * Not recipient.manage: every Member holds that one (V27). Nobody reviews their own request.
 * REMOVE only ever deletes the requester's own RecipientAssignment row; the shared
 * Recipient directory row is untouched.
 */
@Service
@RequiredArgsConstructor
public class DoctorChangeRequestService {

    /** Granted to Company Admin by default (V39): review any MR's request in the company. */
    public static final String APPROVE_ANY_KEY = "recipient.approve";

    private final DoctorChangeRequestRepository doctorChangeRequestRepository;
    private final RecipientRepository recipientRepository;
    private final RecipientAssignmentRepository recipientAssignmentRepository;
    private final UserRepository userRepository;
    private final AccessControlCacheService accessControlCacheService;

    @Transactional
    public DoctorChangeRequestResponse create(CreateDoctorChangeRequest request) {
        User requester = currentUser();
        Company company = requester.getCompany();

        Recipient recipient = null;
        if (request.action() != DoctorChangeAction.ADD) {
            recipient = getOwnCompanyRecipient(request.recipientId(), company.getId());
        }

        DoctorChangeRequest entity = new DoctorChangeRequest(company, requester, request.action(), recipient);
        entity.setProposedName(request.proposedName());
        entity.setProposedPhone(request.proposedPhone());
        entity.setProposedEmail(request.proposedEmail());
        entity.setProposedAddress(request.proposedAddress());
        entity.setProposedDob(request.proposedDob());
        DoctorDetails doctor = DoctorDetails.orEmpty(request.proposedDoctor());
        entity.setProposedQualification(doctor.qualification());
        entity.setProposedRegistrationNumber(doctor.registrationNumber());
        entity.setProposedSpeciality(doctor.speciality());
        entity.setProposedClinicName(doctor.clinicName());
        return DoctorChangeRequestResponse.from(doctorChangeRequestRepository.save(entity));
    }

    @Transactional(readOnly = true)
    public List<DoctorChangeRequestResponse> listMine() {
        User user = currentUser();
        return doctorChangeRequestRepository.findByRequestedBy_IdOrderByCreatedAtDesc(user.getId()).stream()
                .map(DoctorChangeRequestResponse::from)
                .toList();
    }

    /** A manager's own approval queue: their direct reports' pending requests, or every other pending request company-wide if they hold recipient.approve. */
    @Transactional(readOnly = true)
    public List<DoctorChangeRequestResponse> listPendingForReviewer() {
        User reviewer = currentUser();
        if (reviewer.getCompany() == null) {
            return List.of();
        }
        List<DoctorChangeRequest> pending = canReviewCompanyWide(reviewer)
                ? doctorChangeRequestRepository.findByCompany_IdAndStatusOrderByCreatedAtAsc(reviewer.getCompany().getId(), DoctorChangeStatus.PENDING)
                : doctorChangeRequestRepository.findByRequestedBy_ReportsTo_IdAndStatusOrderByCreatedAtAsc(reviewer.getId(), DoctorChangeStatus.PENDING);
        return pending.stream()
                .filter(r -> !r.getRequestedBy().getId().equals(reviewer.getId()))
                .map(DoctorChangeRequestResponse::from)
                .toList();
    }

    @Transactional
    public DoctorChangeRequestResponse approve(UUID id) {
        DoctorChangeRequest entity = reviewableEntity(id);
        User reviewer = currentUser();

        switch (entity.getAction()) {
            case ADD -> applyAdd(entity);
            case EDIT -> applyEdit(entity);
            case REMOVE -> applyRemove(entity);
        }

        entity.setStatus(DoctorChangeStatus.APPROVED);
        entity.setReviewedBy(reviewer);
        entity.setReviewedAt(Instant.now());
        return DoctorChangeRequestResponse.from(entity);
    }

    @Transactional
    public DoctorChangeRequestResponse reject(UUID id, String reason) {
        DoctorChangeRequest entity = reviewableEntity(id);
        User reviewer = currentUser();
        entity.setStatus(DoctorChangeStatus.REJECTED);
        entity.setRejectionReason(reason);
        entity.setReviewedBy(reviewer);
        entity.setReviewedAt(Instant.now());
        return DoctorChangeRequestResponse.from(entity);
    }

    /**
     * A doctor already in the directory under the same registration number is the same doctor:
     * they join the MR's list rather than becoming a duplicate (which the V36 unique index would
     * also refuse).
     */
    private void applyAdd(DoctorChangeRequest entity) {
        Recipient recipient = entity.getProposedRegistrationNumber() == null
                ? null
                : recipientRepository
                        .findFirstByCompany_IdAndRegistrationNumberIgnoreCase(entity.getCompany().getId(), entity.getProposedRegistrationNumber())
                        .orElse(null);
        if (recipient == null) {
            recipient = new Recipient(
                    entity.getCompany(),
                    entity.getProposedName(),
                    entity.getProposedPhone(),
                    entity.getProposedEmail(),
                    entity.getProposedAddress(),
                    entity.getProposedDob(),
                    entity.getRequestedBy());
            recipient.setQualification(entity.getProposedQualification());
            recipient.setRegistrationNumber(entity.getProposedRegistrationNumber());
            recipient.setSpeciality(entity.getProposedSpeciality());
            recipient.setClinicName(entity.getProposedClinicName());
            recipient = recipientRepository.save(recipient);
        }
        entity.setRecipient(recipient);
        if (!recipientAssignmentRepository.existsByRecipient_IdAndMrUser_Id(recipient.getId(), entity.getRequestedBy().getId())) {
            recipientAssignmentRepository.save(new RecipientAssignment(recipient, entity.getRequestedBy()));
        }
    }

    private void applyEdit(DoctorChangeRequest entity) {
        Recipient recipient = entity.getRecipient();
        if (entity.getProposedName() != null && !entity.getProposedName().isBlank()) recipient.setName(entity.getProposedName());
        if (entity.getProposedPhone() != null && !entity.getProposedPhone().isBlank()) recipient.setPhone(entity.getProposedPhone());
        if (entity.getProposedEmail() != null) recipient.setEmail(entity.getProposedEmail());
        if (entity.getProposedAddress() != null) recipient.setAddress(entity.getProposedAddress());
        if (entity.getProposedDob() != null) recipient.setDateOfBirth(entity.getProposedDob());
        if (entity.getProposedQualification() != null) recipient.setQualification(entity.getProposedQualification());
        if (entity.getProposedSpeciality() != null) recipient.setSpeciality(entity.getProposedSpeciality());
        if (entity.getProposedClinicName() != null) recipient.setClinicName(entity.getProposedClinicName());
        String registration = entity.getProposedRegistrationNumber();
        if (registration != null) {
            recipientRepository.findFirstByCompany_IdAndRegistrationNumberIgnoreCase(recipient.getCompany().getId(), registration)
                    .filter(other -> !other.getId().equals(recipient.getId()))
                    .ifPresent(other -> {
                        throw new RecipientAlreadyExistsException(other.getName());
                    });
            recipient.setRegistrationNumber(registration);
        }
    }

    private void applyRemove(DoctorChangeRequest entity) {
        recipientAssignmentRepository
                .findByRecipient_IdAndMrUser_Id(entity.getRecipient().getId(), entity.getRequestedBy().getId())
                .ifPresent(recipientAssignmentRepository::delete);
    }

    private DoctorChangeRequest reviewableEntity(UUID id) {
        DoctorChangeRequest entity = doctorChangeRequestRepository.findById(id).orElseThrow(() -> new DoctorChangeRequestNotFoundException(id));
        User reviewer = currentUser();
        // Another company's request is a 404, the same as one that doesn't exist.
        if (reviewer.getCompany() == null || !entity.getCompany().getId().equals(reviewer.getCompany().getId())) {
            throw new DoctorChangeRequestNotFoundException(id);
        }
        if (entity.getRequestedBy().getId().equals(reviewer.getId())) {
            throw new AccessDeniedException("You can't review your own request");
        }
        boolean isDirectManager = entity.getRequestedBy().getReportsTo() != null
                && entity.getRequestedBy().getReportsTo().getId().equals(reviewer.getId());
        if (!isDirectManager && !canReviewCompanyWide(reviewer)) {
            throw new AccessDeniedException("Not authorized to review this request");
        }
        if (entity.getStatus() != DoctorChangeStatus.PENDING) {
            throw new DoctorChangeRequestAlreadyReviewedException(id);
        }
        return entity;
    }

    private boolean canReviewCompanyWide(User user) {
        return accessControlCacheService.resolveEffectiveKeys(user).contains(APPROVE_ANY_KEY);
    }

    private Recipient getOwnCompanyRecipient(UUID recipientId, UUID companyId) {
        if (recipientId == null) {
            throw new RecipientNotFoundException(null);
        }
        Recipient recipient = recipientRepository.findById(recipientId).orElseThrow(() -> new RecipientNotFoundException(recipientId));
        if (!recipient.getCompany().getId().equals(companyId)) {
            throw new RecipientNotFoundException(recipientId);
        }
        return recipient;
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
