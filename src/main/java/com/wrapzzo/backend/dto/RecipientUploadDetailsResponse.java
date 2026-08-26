package com.wrapzzo.backend.dto;

import java.util.List;

/** Public-facing (unauthenticated) view of what a recipient sees when they open their photo-upload link. */
public record RecipientUploadDetailsResponse(
        String recipientName,
        String companyName,
        String giftName,
        String giftDescription,
        List<String> giftImageUrls,
        boolean alreadyUploaded) {
}
