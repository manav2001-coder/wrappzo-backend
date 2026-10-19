package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** key comes from a prior presign call; the vendor's browser has already PUT the file there directly. */
public record UploadOrderDesignRequest(@NotBlank String key, String fileName) {
}
