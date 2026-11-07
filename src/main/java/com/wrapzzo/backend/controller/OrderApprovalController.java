package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OrderApprovalDecisionRequest;
import com.wrapzzo.backend.dto.OrderApprovalSettingsRequest;
import com.wrapzzo.backend.dto.OrderApprovalSettingsResponse;
import com.wrapzzo.backend.dto.PendingOrderApprovalResponse;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.OrderApprovalService;
import com.wrapzzo.backend.service.OrderApprovalSettingsService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Order approval: the queue and the decision for the approver, and the on/off + who-approves setup for the company admin. */
@RestController
@RequestMapping("/api/order-approvals")
@RequiredArgsConstructor
public class OrderApprovalController {

    private final OrderApprovalService orderApprovalService;
    private final OrderApprovalSettingsService settingsService;

    @GetMapping("/pending/page")
    @RequiresPermission(Keys.APPROVAL_VIEW)
    public PageResponse<PendingOrderApprovalResponse> pendingPage(ListQueryParams params) {
        return orderApprovalService.pagePending(params.toQuery());
    }

    @PostMapping("/{orderId}/approve")
    @PreAuthorize("hasAnyAuthority('" + Keys.APPROVAL_DECIDE + "', '" + Keys.ARTWORK_FINAL_APPROVE + "')")
    public PendingOrderApprovalResponse approve(@PathVariable UUID orderId, @Valid @RequestBody(required = false) OrderApprovalDecisionRequest request) {
        return orderApprovalService.approve(orderId, request != null ? request.comment() : null);
    }

    @PostMapping("/{orderId}/reject")
    @PreAuthorize("hasAnyAuthority('" + Keys.APPROVAL_DECIDE + "', '" + Keys.ARTWORK_FINAL_APPROVE + "')")
    public PendingOrderApprovalResponse reject(@PathVariable UUID orderId, @Valid @RequestBody(required = false) OrderApprovalDecisionRequest request) {
        return orderApprovalService.reject(orderId, request != null ? request.comment() : null);
    }

    @GetMapping("/settings")
    @RequiresPermission(Keys.ORDER_APPROVAL_VIEW)
    public OrderApprovalSettingsResponse settings() {
        return settingsService.get();
    }

    @PutMapping("/settings")
    @RequiresPermission(Keys.ORDER_APPROVAL_CONFIGURE)
    public OrderApprovalSettingsResponse setSettings(@Valid @RequestBody OrderApprovalSettingsRequest request) {
        return settingsService.set(request);
    }
}
