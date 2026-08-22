package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.AttachGiftImageRequest;
import com.wrapzzo.backend.dto.CreateGiftRequest;
import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.dto.PresignGiftImageUploadRequest;
import com.wrapzzo.backend.dto.PresignGiftImageUploadResponse;
import com.wrapzzo.backend.dto.UpdateGiftRequest;
import com.wrapzzo.backend.security.CurrentVendor;
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

/** A vendor's own gift submissions -- gated by ROLE_VENDOR in SecurityConfig, scoped to CurrentVendor.id() throughout. */
@RestController
@RequestMapping("/api/vendor/gifts")
@RequiredArgsConstructor
public class VendorGiftController {

    private final GiftService giftService;

    @PostMapping("/add")
    public GiftResponse add(@Valid @RequestBody CreateGiftRequest request) {
        return giftService.createForVendor(request, CurrentVendor.id());
    }

    @GetMapping("/list")
    public List<GiftResponse> list() {
        return giftService.listForVendor(CurrentVendor.id());
    }

    @GetMapping("/view/{id}")
    public GiftResponse view(@PathVariable UUID id) {
        return giftService.getForVendor(id, CurrentVendor.id());
    }

    @PostMapping("/edit/{id}")
    public GiftResponse edit(@PathVariable UUID id, @Valid @RequestBody UpdateGiftRequest request) {
        return giftService.updateForVendor(id, request, CurrentVendor.id());
    }

    @PostMapping("/presignImage/{id}")
    public PresignGiftImageUploadResponse presignImage(
            @PathVariable UUID id, @Valid @RequestBody PresignGiftImageUploadRequest request) {
        return giftService.presignImageUploadForVendor(id, CurrentVendor.id(), request.contentType());
    }

    @PostMapping("/addImage/{id}")
    public GiftResponse addImage(@PathVariable UUID id, @Valid @RequestBody AttachGiftImageRequest request) {
        return giftService.attachImageForVendor(id, CurrentVendor.id(), request.key(), request.displayOrder());
    }

    @DeleteMapping("/removeImage/{id}/{imageId}")
    public GiftResponse removeImage(@PathVariable UUID id, @PathVariable UUID imageId) {
        return giftService.deleteImageForVendor(id, CurrentVendor.id(), imageId);
    }
}
