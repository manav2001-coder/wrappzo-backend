package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.RecurringOrderPlanResponse;
import com.wrapzzo.backend.service.RecurringOrderService;
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

/** A Quick Gift repeated every month -- the requester's own plans only. */
@RestController
@RequestMapping("/api/recurring-orders")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('quickgift.create')")
public class RecurringOrderController {

    private final RecurringOrderService recurringOrderService;

    @PostMapping
    public RecurringOrderPlanResponse create(@Valid @RequestBody CreateQuickGiftRequest request) {
        return recurringOrderService.create(request);
    }

    @GetMapping("/mine")
    public List<RecurringOrderPlanResponse> mine() {
        return recurringOrderService.listMine();
    }

    @PostMapping("/{id}/pause")
    public RecurringOrderPlanResponse pause(@PathVariable UUID id) {
        return recurringOrderService.pause(id);
    }

    @PostMapping("/{id}/resume")
    public RecurringOrderPlanResponse resume(@PathVariable UUID id) {
        return recurringOrderService.resume(id);
    }

    @PostMapping("/{id}/cancel")
    public RecurringOrderPlanResponse cancel(@PathVariable UUID id) {
        return recurringOrderService.cancel(id);
    }

    @PostMapping("/{id}/placeNextNow")
    public RecurringOrderPlanResponse placeNextNow(@PathVariable UUID id) {
        return recurringOrderService.placeNextNow(id);
    }
}
