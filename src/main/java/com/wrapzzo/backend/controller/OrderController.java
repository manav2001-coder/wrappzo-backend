package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OrderResponse;
import com.wrapzzo.backend.dto.RequestPhotoLinkResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.OrderService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only listing for now -- PRD 2.10/2.11. Company-scoped list of commerce Orders.
 * order.track and order.design.review are narrower alternatives to the campaign.* keys
 * below -- a company scoped to just tracking/design-review (no campaign management)
 * still needs to list/get orders to do either.
 */
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@RequiresPermission(Keys.ORDER_VIEW)
public class OrderController {

    private final OrderService orderService;

    @GetMapping
    public List<OrderResponse> list() {
        return orderService.list();
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable UUID id) {
        return orderService.get(id);
    }

    /** Same visibility as list()/get() -- anyone who can already see this order can chase a missing photo. */
    @PostMapping("/{id}/requestPhotoLink")
    public RequestPhotoLinkResponse requestPhotoLink(@PathVariable UUID id) {
        return new RequestPhotoLinkResponse(orderService.requestPhotoLink(id));
    }
}
