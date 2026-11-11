package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.OverridableArtworkResponse;
import com.wrapzzo.backend.dto.PendingArtworkApprovalResponse;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.OrderDesignService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
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

    @GetMapping("/pending/page")
    @PreAuthorize("hasAuthority('" + Keys.APPROVAL_VIEW + "')")
    public PageResponse<PendingArtworkApprovalResponse> pendingPage(ListQueryParams params) {
        return orderDesignService.pagePendingFinalApproval(params.toQuery());
    }

    /** Proofs still waiting on the MR or a level below the caller, which the caller may approve in place of the people there. */
    @GetMapping("/overridable/page")
    @RequiresPermission(Keys.ARTWORK_OVERRIDE)
    public PageResponse<OverridableArtworkResponse> overridablePage(ListQueryParams params) {
        return orderDesignService.pageOverridable(params.toQuery());
    }
}
