package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.PresignSubmissionAttachmentRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.RecipientUploadDetailsResponse;
import com.wrapzzo.backend.dto.SubmitRecipientPhotoRequest;
import com.wrapzzo.backend.service.QuickGiftUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fully unauthenticated -- reached by a Quick Gift recipient clicking an emailed link, with
 * no Wrapzzo account of their own (see SecurityConfig's /api/public/** permitAll).
 */
@RestController
@RequestMapping("/api/public/quick-gift-upload/{token}")
@RequiredArgsConstructor
public class QuickGiftUploadController {

    private final QuickGiftUploadService quickGiftUploadService;

    @GetMapping
    public RecipientUploadDetailsResponse details(@PathVariable String token) {
        return quickGiftUploadService.getDetails(token);
    }

    @PostMapping("/presignAttachment")
    public PresignSubmissionAttachmentResponse presign(@PathVariable String token, @Valid @RequestBody PresignSubmissionAttachmentRequest request) {
        return quickGiftUploadService.presignUpload(token, request.contentType());
    }

    @PostMapping("/submit")
    public void submit(@PathVariable String token, @Valid @RequestBody SubmitRecipientPhotoRequest request) {
        quickGiftUploadService.submitPhoto(token, request);
    }
}
