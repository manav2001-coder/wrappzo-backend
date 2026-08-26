package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.PresignSubmissionAttachmentRequest;
import com.wrapzzo.backend.dto.PresignSubmissionAttachmentResponse;
import com.wrapzzo.backend.dto.RecipientUploadDetailsResponse;
import com.wrapzzo.backend.dto.SubmitRecipientPhotoRequest;
import com.wrapzzo.backend.service.RecipientUploadService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Fully unauthenticated -- reached by a recipient clicking an emailed link, with no
 * Wrapzzo account of their own. Authorization here is the opaque token itself, not a
 * login (see SecurityConfig's /api/public/** permitAll).
 */
@RestController
@RequestMapping("/api/public/recipient-upload/{token}")
@RequiredArgsConstructor
public class RecipientUploadController {

    private final RecipientUploadService recipientUploadService;

    @GetMapping
    public RecipientUploadDetailsResponse details(@PathVariable String token) {
        return recipientUploadService.getDetails(token);
    }

    @PostMapping("/presignAttachment")
    public PresignSubmissionAttachmentResponse presign(@PathVariable String token, @Valid @RequestBody PresignSubmissionAttachmentRequest request) {
        return recipientUploadService.presignUpload(token, request.contentType());
    }

    @PostMapping("/submit")
    public void submit(@PathVariable String token, @Valid @RequestBody SubmitRecipientPhotoRequest request) {
        recipientUploadService.submitPhoto(token, request);
    }
}
