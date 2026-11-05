package com.wrapzzo.backend.service;

import com.wrapzzo.backend.model.User;
import java.util.Map;

/**
 * How files tied to an order are named, so the vendor (and anyone who downloads them) can tell at a glance whose they are:
 * the doctor's pcode, then the ordering AM's code. A proof is PCODE_AMCODE_vN.pdf; the recipient's photo is PCODE_AMCODE_photo.jpg.
 */
public final class FileNaming {

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", "jpg", "image/jpg", "jpg", "image/png", "png", "image/webp", "webp", "image/gif", "gif",
            "image/heic", "heic", "image/heif", "heif");

    private FileNaming() {
    }

    /** Letters and digits only, runs of anything else collapsed to one hyphen: safe in a key, a URL and a download. */
    public static String part(String value) {
        String part = value.trim().replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return part.isEmpty() ? "NA" : part;
    }

    /** The AM's employee code, or their name when they have none. */
    public static String amCode(User am) {
        return am.getEmployeeCode() != null && !am.getEmployeeCode().isBlank() ? am.getEmployeeCode() : am.getName();
    }

    /** PCODE_AMCODE_photo.ext -- the pcode part is "NA" until a pcode is known. */
    public static String photoName(String pcode, User am, String contentType) {
        String type = contentType == null ? "" : contentType.split(";")[0].trim().toLowerCase();
        return "%s_%s_photo.%s".formatted(part(pcode == null ? "" : pcode), part(amCode(am)), EXTENSION_BY_CONTENT_TYPE.getOrDefault(type, "jpg"));
    }

    /** The last path segment of a storage key -- the name the file downloads as. */
    public static String lastSegment(String key) {
        return key.substring(key.lastIndexOf('/') + 1);
    }
}
