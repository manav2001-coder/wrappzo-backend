package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AttachGiftImageRequest;
import com.wrapzzo.backend.dto.CreateGiftRequest;
import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.dto.PresignGiftImageUploadRequest;
import com.wrapzzo.backend.dto.PresignGiftImageUploadResponse;
import com.wrapzzo.backend.dto.UpdateGiftRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.GiftService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Admin-side catalogue management -- Super Admin only, via gift.manage. */
@RestController
@RequestMapping("/api/platform/gifts")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @PostMapping("/add")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_CREATE)
    public GiftResponse add(@Valid @RequestBody CreateGiftRequest request) {
        return giftService.create(request);
    }

    @GetMapping("/list")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_VIEW)
    public List<GiftResponse> list() {
        return giftService.list();
    }

    @GetMapping("/view/{id}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_VIEW)
    public GiftResponse view(@PathVariable UUID id) {
        return giftService.get(id);
    }

    @PostMapping("/edit/{id}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_EDIT)
    public GiftResponse edit(@PathVariable UUID id, @Valid @RequestBody UpdateGiftRequest request) {
        return giftService.update(id, request);
    }

    @PostMapping("/activate/{id}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_STATUS)
    public GiftResponse activate(@PathVariable UUID id) {
        return giftService.setActive(id, true);
    }

    @PostMapping("/deactivate/{id}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_STATUS)
    public GiftResponse deactivate(@PathVariable UUID id) {
        return giftService.setActive(id, false);
    }

    @PostMapping("/presignImage/{id}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_EDIT)
    public PresignGiftImageUploadResponse presignImage(
            @PathVariable UUID id, @Valid @RequestBody PresignGiftImageUploadRequest request) {
        return giftService.presignImageUpload(id, request.contentType());
    }

    @PostMapping("/addImage/{id}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_EDIT)
    public GiftResponse addImage(@PathVariable UUID id, @Valid @RequestBody AttachGiftImageRequest request) {
        return giftService.attachImage(id, request.key(), request.displayOrder());
    }

    @DeleteMapping("/removeImage/{id}/{imageId}")
    @RequiresPermission(Keys.PLATFORM_PRODUCT_EDIT)
    public GiftResponse removeImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        return giftService.deleteImage(id, imageId);
    }
}
