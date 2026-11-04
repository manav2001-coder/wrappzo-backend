package com.wrapzzo.backend.dto;

import jakarta.validation.constraints.NotBlank;

/** key comes from a prior presign call; the vendor's browser has already PUT the file there directly. The file name is the key's own. */
public record UploadOrderDesignRequest(@NotBlank String key) {
}
