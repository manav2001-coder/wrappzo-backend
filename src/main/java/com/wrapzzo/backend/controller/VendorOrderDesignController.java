package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OrderDesignResponse;
import com.wrapzzo.backend.dto.PresignOrderDesignRequest;
import com.wrapzzo.backend.dto.PresignOrderDesignResponse;
import com.wrapzzo.backend.dto.UploadOrderDesignRequest;
import com.wrapzzo.backend.security.CurrentVendor;
import com.wrapzzo.backend.security.VendorEndpoint;
import com.wrapzzo.backend.service.OrderDesignService;
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

/** A vendor's own design-proof uploads for an order assigned to them -- gated by ROLE_VENDOR in SecurityConfig, scoped to CurrentVendor.id() throughout. */
@RestController
@RequestMapping("/api/vendor/orders/{orderId}/designs")
@RequiredArgsConstructor
@VendorEndpoint
public class VendorOrderDesignController {

    private final OrderDesignService orderDesignService;

    @PostMapping("/presign")
    public PresignOrderDesignResponse presign(@PathVariable UUID orderId, @Valid @RequestBody PresignOrderDesignRequest request) {
        return orderDesignService.presignForVendor(orderId, CurrentVendor.id(), request.contentType());
    }

    @PostMapping
    public OrderDesignResponse upload(@PathVariable UUID orderId, @Valid @RequestBody UploadOrderDesignRequest request) {
        return orderDesignService.uploadForVendor(orderId, CurrentVendor.id(), request.key(), request.fileName());
    }

    @GetMapping
    public List<OrderDesignResponse> list(@PathVariable UUID orderId) {
        return orderDesignService.listForVendor(orderId, CurrentVendor.id());
    }
}
