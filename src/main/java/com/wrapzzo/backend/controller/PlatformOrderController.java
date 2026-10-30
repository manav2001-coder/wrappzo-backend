package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AssignOrderVendorRequest;
import com.wrapzzo.backend.dto.BulkAssignResponse;
import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.dto.RequestPhotoLinkResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.OrderService;
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

/** Admin-side Fulfillment & Operations (PRD Part 1.5) -- every company's orders, and routing them to a vendor. */
@RestController
@RequestMapping("/api/platform/orders")
@RequiredArgsConstructor
public class PlatformOrderController {

    private final OrderService orderService;

    @GetMapping
    @RequiresPermission(Keys.PLATFORM_ORDER_VIEW)
    public List<OrderResponse> list() {
        return orderService.listAllForPlatform();
    }

    @PostMapping("/{id}/requestPhotoLink")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public RequestPhotoLinkResponse requestPhotoLink(@PathVariable UUID id) {
        return new RequestPhotoLinkResponse(orderService.requestPhotoLinkForPlatform(id));
    }

    @PostMapping("/{id}/assign")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public OrderResponse assign(@PathVariable UUID id, @Valid @RequestBody AssignOrderVendorRequest request) {
        return orderService.assignVendorManually(id, request.vendorId());
    }

    @PostMapping("/{id}/autoAssign")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public OrderResponse autoAssign(@PathVariable UUID id) {
        return orderService.autoAssignVendor(id);
    }

    @PostMapping("/campaign/{campaignId}/assign")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public BulkAssignResponse assignCampaign(@PathVariable UUID campaignId, @Valid @RequestBody AssignOrderVendorRequest request) {
        return orderService.assignCampaignVendor(campaignId, request.vendorId());
    }

    @PostMapping("/campaign/{campaignId}/autoAssign")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public BulkAssignResponse autoAssignCampaign(@PathVariable UUID campaignId) {
        return orderService.autoAssignCampaign(campaignId);
    }

    /** Same bulk-assign convenience as a campaign's, but for a company's Quick Gift orders -- these have no campaign to key off, so companyId stands in for it. */
    @PostMapping("/quickGift/{companyId}/assign")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public BulkAssignResponse assignQuickGift(@PathVariable UUID companyId, @Valid @RequestBody AssignOrderVendorRequest request) {
        return orderService.assignQuickGiftVendor(companyId, request.vendorId());
    }

    @PostMapping("/quickGift/{companyId}/autoAssign")
    @RequiresPermission(Keys.PLATFORM_ORDER_MANAGE)
    public BulkAssignResponse autoAssignQuickGift(@PathVariable UUID companyId) {
        return orderService.autoAssignQuickGift(companyId);
    }
}
