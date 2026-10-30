package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.service.OrderDesignService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A manager's queue of MR-approved artwork waiting on their level of approval. No key requirement
 * of its own: OrderDesignService returns only proofs whose current level waits on the caller (or
 * every one, for an order.design.final holder covering). Approving and rejecting go through
 * OrderDesignController.
 */
@RestController
@RequestMapping("/api/artwork-approvals")
@RequiredArgsConstructor
public class ArtworkApprovalController {

    private final OrderDesignService orderDesignService;

    @GetMapping("/pending")
    public List<PendingArtworkApprovalResponse> pending() {
        return orderDesignService.listPendingFinalApproval();
    }
}
