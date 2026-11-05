package com.wrapzzo.backend.dto;

/** {@code fileName} is what the file is stored (and downloads) as: PCODE_AMCODE_photo.ext. */
public record PresignQuickGiftPhotoResponse(String uploadUrl, String key, String fileName) {
}
