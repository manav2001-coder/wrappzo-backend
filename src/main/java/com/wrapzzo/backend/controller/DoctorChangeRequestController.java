package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateDoctorChangeRequest;
import com.wrapzzo.backend.dto.DoctorChangeRequestResponse;
import com.wrapzzo.backend.dto.RejectDoctorChangeRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.DoctorChangeRequestService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MR doctor-change requests. Creating/listing-mine needs the same baseline as managing your own
 * doctor list (my_doctors.manage). Reviewing needs my_doctors.approve; whose requests a reviewer
 * then sees is visibility, not access, and stays in DoctorChangeRequestService (the requester's
 * direct manager, or everyone's for a company-wide approver).
 */
@RestController
@RequestMapping("/api/doctor-requests")
@RequiredArgsConstructor
public class DoctorChangeRequestController {

    private final DoctorChangeRequestService doctorChangeRequestService;

    @PostMapping
    @RequiresPermission(Keys.MY_DOCTORS_MANAGE)
    public DoctorChangeRequestResponse create(@Valid @RequestBody CreateDoctorChangeRequest request) {
        return doctorChangeRequestService.create(request);
    }

    @GetMapping("/mine")
    @RequiresPermission(Keys.MY_DOCTORS_MANAGE)
    public List<DoctorChangeRequestResponse> mine() {
        return doctorChangeRequestService.listMine();
    }

    @GetMapping("/pending-review")
    @RequiresPermission(Keys.MY_DOCTORS_APPROVE)
    public List<DoctorChangeRequestResponse> pendingReview() {
        return doctorChangeRequestService.listPendingForReviewer();
    }

    @PostMapping("/{id}/approve")
    @RequiresPermission(Keys.MY_DOCTORS_APPROVE)
    public DoctorChangeRequestResponse approve(@PathVariable UUID id) {
        return doctorChangeRequestService.approve(id);
    }

    @PostMapping("/{id}/reject")
    @RequiresPermission(Keys.MY_DOCTORS_APPROVE)
    public DoctorChangeRequestResponse reject(@PathVariable UUID id, @Valid @RequestBody RejectDoctorChangeRequest request) {
        return doctorChangeRequestService.reject(id, request.reason());
    }
}
