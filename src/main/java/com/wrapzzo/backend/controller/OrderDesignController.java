package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.RejectOrderDesignRequest;
import com.wrapzzo.backend.service.OrderDesignService;
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

/** Company-side design review for an order -- gated by order.design.review, company-scoped throughout. */
@RestController
@RequestMapping("/api/orders/{orderId}/designs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('order.design.review')")
public class OrderDesignController {

    private final OrderDesignService orderDesignService;

    @GetMapping
    public List<OrderDesignResponse> list(@PathVariable UUID orderId) {
        return orderDesignService.listForCompany(orderId);
    }

    @PostMapping("/{designId}/approve")
    public OrderDesignResponse approve(@PathVariable UUID orderId, @PathVariable UUID designId) {
        return orderDesignService.approve(orderId, designId);
    }

    @PostMapping("/{designId}/reject")
    public OrderDesignResponse reject(
            @PathVariable UUID orderId, @PathVariable UUID designId, @Valid @RequestBody RejectOrderDesignRequest request) {
        return orderDesignService.reject(orderId, designId, request.comment());
    }
}
