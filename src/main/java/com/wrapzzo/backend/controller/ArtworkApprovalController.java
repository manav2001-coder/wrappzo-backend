package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.service.OrderDesignService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A senior's queue of MR-approved artwork awaiting the final approval. No key requirement of its
 * own: OrderDesignService returns only proofs from the caller's direct reports (or every one, for
 * an order.design.final holder). Approving and rejecting go through OrderDesignController.
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
