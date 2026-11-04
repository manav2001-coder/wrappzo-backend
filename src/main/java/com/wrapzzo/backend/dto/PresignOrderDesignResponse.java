package com.wrapzzo.backend.dto;

/** fileName: what the proof will be called -- PCODE_AMCODE_vN.pdf; it is the last part of the key, so a download carries it too. */
public record PresignOrderDesignResponse(String uploadUrl, String key, String fileName) {
}
