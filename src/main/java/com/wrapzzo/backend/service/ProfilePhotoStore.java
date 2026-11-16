package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.PresignLogoUploadResponse;
import com.wrapzzo.backend.exception.InvalidProfilePhotoException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Profile pictures (a person's) and logos (a vendor's): where they live in storage, and the checks before one is accepted.
 * The browser uploads straight to storage with a presigned URL, so the file is only trusted after it has been looked at
 * here: it must be the caller's own upload, small, and really a JPEG or PNG -- not a file renamed to look like one.
 */
@Component
@RequiredArgsConstructor
public class ProfilePhotoStore {

    static final long MAX_BYTES = 2L * 1024 * 1024;
    private static final Duration UPLOAD_URL_EXPIRY = Duration.ofMinutes(10);
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of("image/jpeg", "jpg", "image/jpg", "jpg", "image/png", "png");
    private static final byte[] JPEG_MAGIC = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 'P', 'N', 'G'};

    private final FileStorageService fileStorageService;

    /** @param folder "users" or "vendors": the owner's pictures all sit under it, so a key can be tied to whoever asked for it. */
    public PresignLogoUploadResponse presign(String folder, UUID ownerId, String contentType) {
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType == null ? "" : contentType.toLowerCase());
        if (extension == null) {
            throw new InvalidProfilePhotoException("The picture must be a JPG or PNG image");
        }
        String key = "%s/%s/profile-%s.%s".formatted(folder, ownerId, UUID.randomUUID(), extension);
        return new PresignLogoUploadResponse(fileStorageService.presignUpload(key, contentType.toLowerCase(), UPLOAD_URL_EXPIRY), key);
    }

    /** Throws {@link InvalidProfilePhotoException} unless the key is the owner's own, and the stored file is a small JPEG or PNG. */
    public void requireUsable(String folder, UUID ownerId, String key) {
        if (key == null || !key.startsWith("%s/%s/profile-".formatted(folder, ownerId)) || key.contains("..")) {
            throw new InvalidProfilePhotoException("That picture was not uploaded for your account");
        }
        FileStorageService.StoredObject stored = fileStorageService.inspect(key, 4);
        if (stored == null) {
            throw new InvalidProfilePhotoException("The picture was not uploaded; try again");
        }
        if (stored.size() > MAX_BYTES) {
            throw new InvalidProfilePhotoException("The picture is too large; the limit is 2 MB");
        }
        if (!startsWith(stored.head(), JPEG_MAGIC) && !startsWith(stored.head(), PNG_MAGIC)) {
            throw new InvalidProfilePhotoException("The picture must be a JPG or PNG image");
        }
    }

    public String urlFor(String key) {
        return key == null ? null : fileStorageService.publicUrl(key);
    }

    public void delete(String key) {
        if (key != null) {
            fileStorageService.delete(key);
        }
    }

    private static boolean startsWith(byte[] head, byte[] magic) {
        if (head == null || head.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (head[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
