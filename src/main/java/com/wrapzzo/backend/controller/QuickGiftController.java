package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateQuickGiftRequest;
import com.wrapzzo.backend.dto.PresignQuickGiftPhotoRequest;
import com.wrapzzo.backend.dto.PresignQuickGiftPhotoResponse;
import com.wrapzzo.backend.dto.QuickGiftRequestResponse;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.dto.RejectQuickGiftRequest;
import com.wrapzzo.backend.dto.RequestPhotoLinkResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.QuickGiftService;
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

/** Quick Gift (PRD Part 2, quick path) -- requesting is Company Admin and Member alike; approving is Company Admin only. */
@RestController
@RequestMapping("/api/quick-gifts")
@RequiredArgsConstructor
public class QuickGiftController {

    private final QuickGiftService quickGiftService;

    @PostMapping("/presignPhoto")
    @RequiresPermission(Keys.QUICK_GIFT_CREATE)
    public PresignQuickGiftPhotoResponse presignPhoto(@Valid @RequestBody PresignQuickGiftPhotoRequest request) {
        return quickGiftService.presignPhoto(request.contentType(), request.pcode(), request.number() != null ? request.number() : 1);
    }

    @PostMapping
    @RequiresPermission(Keys.QUICK_GIFT_CREATE)
    public QuickGiftRequestResponse create(@Valid @RequestBody CreateQuickGiftRequest request) {
        return quickGiftService.create(request);
    }

    /** One page of the caller's own requests: ?page=0&size=25&search=&sort=createdAt&dir=desc&filters=[{field,operator,value}]. */
    @GetMapping("/mine/page")
    @RequiresPermission(Keys.QUICK_GIFT_CREATE)
    public PageResponse<QuickGiftRequestResponse> minePage(ListQueryParams params) {
        return quickGiftService.pageMine(params.toQuery());
    }

    @GetMapping("/{id}")
    @RequiresPermission(Keys.QUICK_GIFT_CREATE)
    public QuickGiftRequestResponse get(@PathVariable UUID id) {
        return quickGiftService.get(id);
    }

    @PostMapping("/{id}/requestPhotoLink")
    @RequiresPermission(Keys.QUICK_GIFT_CREATE)
    public RequestPhotoLinkResponse requestPhotoLink(@PathVariable UUID id) {
        return new RequestPhotoLinkResponse(quickGiftService.requestPhotoLinkAgain(id));
    }
}
