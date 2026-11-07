package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.UpdateOrderStatusRequest;
import com.wrapzzo.backend.dto.VendorOrderDivisionResponse;
import com.wrapzzo.backend.dto.VendorOrderResponse;
import com.wrapzzo.backend.dto.VendorOrderSummaryResponse;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import org.springframework.web.bind.annotation.RequestParam;
import com.wrapzzo.backend.security.CurrentVendor;
import com.wrapzzo.backend.security.VendorEndpoint;
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

/** A vendor's own fulfillment queue -- gated by ROLE_VENDOR in SecurityConfig, scoped to CurrentVendor.id() throughout. */
@RestController
@RequestMapping("/api/vendor/orders")
@RequiredArgsConstructor
@VendorEndpoint
public class VendorOrderController {

    private final OrderService orderService;

    /** One page of the vendor's orders: ?page=0&size=25&search=&sort=createdAt&dir=desc&filters=[{field,operator,value}] (stage and divisionId among them). */
    @GetMapping("/page")
    public PageResponse<VendorOrderResponse> page(ListQueryParams params) {
        return orderService.pageForVendor(CurrentVendor.id(), params.toQuery());
    }

    /** How many orders are at each stage, optionally within one division. */
    @GetMapping("/summary")
    public VendorOrderSummaryResponse summary(@RequestParam(required = false) UUID divisionId) {
        return orderService.summaryForVendor(CurrentVendor.id(), divisionId);
    }

    /** The divisions the vendor's orders come from. */
    @GetMapping("/divisions")
    public List<VendorOrderDivisionResponse> divisions() {
        return orderService.orderDivisionsForVendor(CurrentVendor.id());
    }

    @GetMapping("/view/{id}")
    public VendorOrderResponse view(@PathVariable UUID id) {
        return orderService.getForVendor(id, CurrentVendor.id());
    }

    @PostMapping("/updateStatus/{id}")
    public VendorOrderResponse updateStatus(@PathVariable UUID id, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return orderService.updateStatusForVendor(id, CurrentVendor.id(), request.status());
    }
}
