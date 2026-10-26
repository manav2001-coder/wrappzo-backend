package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateDoctorChangeRequest;
import com.wrapzzo.backend.dto.DoctorChangeRequestResponse;
import com.wrapzzo.backend.dto.RejectDoctorChangeRequest;
import com.wrapzzo.backend.service.DoctorChangeRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MR doctor-change requests. Creating/listing-mine needs the same baseline as sending a
 * gift (quickgift.create) or full directory access (recipient.manage) -- not a new,
 * separate key just to propose a change. The review endpoints have no key requirement of
 * their own: DoctorChangeRequestService enforces that the caller is either the requester's
 * direct manager or a recipient.manage holder, and simply returns nothing for anyone else.
 */
@RestController
@RequestMapping("/api/doctor-requests")
@RequiredArgsConstructor
public class DoctorChangeRequestController {

    private final DoctorChangeRequestService doctorChangeRequestService;

    @PostMapping
    @PreAuthorize("hasAnyAuthority('recipient.manage', 'quickgift.create')")
    public DoctorChangeRequestResponse create(@Valid @RequestBody CreateDoctorChangeRequest request) {
        return doctorChangeRequestService.create(request);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('recipient.manage', 'quickgift.create')")
    public List<DoctorChangeRequestResponse> mine() {
        return doctorChangeRequestService.listMine();
    }

    @GetMapping("/pending-review")
    public List<DoctorChangeRequestResponse> pendingReview() {
        return doctorChangeRequestService.listPendingForReviewer();
    }

    @PostMapping("/{id}/approve")
    public DoctorChangeRequestResponse approve(@PathVariable UUID id) {
        return doctorChangeRequestService.approve(id);
    }

    @PostMapping("/{id}/reject")
    public DoctorChangeRequestResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectDoctorChangeRequest request) {
        return doctorChangeRequestService.reject(id, request.reason());
    }
}
