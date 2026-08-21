package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateGiftRequest;
import com.wrapzzo.backend.dto.GiftImageResponse;
import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.dto.PresignGiftImageUploadResponse;
import com.wrapzzo.backend.dto.UpdateGiftRequest;
import com.wrapzzo.backend.exception.GiftImageNotFoundException;
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftImage;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.GiftImageRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GiftService {

    private static final Duration IMAGE_UPLOAD_URL_EXPIRY = Duration.ofMinutes(5);

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final GiftRepository giftRepository;
    private final GiftImageRepository giftImageRepository;
    private final UserRepository userRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public GiftResponse create(CreateGiftRequest request) {
        User creator = currentUser();
        Gift gift = giftRepository.save(
                new Gift(request.name(), request.category(), request.description(), request.moq(), request.leadTimeDays(), creator));
        return toResponse(gift);
    }

    public List<GiftResponse> list() {
        return giftRepository.findAll().stream().map(this::toResponse).toList();
    }

    public List<GiftResponse> listActive() {
        return giftRepository.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    public GiftResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    public GiftResponse getActive(UUID id) {
        Gift gift = getEntity(id);
        if (!gift.isActive()) {
            throw new GiftNotFoundException(id);
        }
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse update(UUID id, UpdateGiftRequest request) {
        Gift gift = getEntity(id);
        gift.setName(request.name());
        gift.setCategory(request.category());
        gift.setDescription(request.description());
        gift.setMoq(request.moq());
        gift.setLeadTimeDays(request.leadTimeDays());
        gift.setUpdatedBy(currentUser());
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse setActive(UUID id, boolean active) {
        Gift gift = getEntity(id);
        gift.setActive(active);
        gift.setUpdatedBy(currentUser());
        return toResponse(gift);
    }

    public PresignGiftImageUploadResponse presignImageUpload(UUID giftId, String contentType) {
        getEntity(giftId);
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        String key = "gifts/%s/image-%s.%s".formatted(giftId, UUID.randomUUID(), extension);
        String uploadUrl = fileStorageService.presignUpload(key, contentType, IMAGE_UPLOAD_URL_EXPIRY);
        return new PresignGiftImageUploadResponse(uploadUrl, key);
    }

    @Transactional
    public GiftResponse attachImage(UUID giftId, String key, Integer displayOrder) {
        Gift gift = getEntity(giftId);
        int order = displayOrder != null ? displayOrder : giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(giftId).size();
        giftImageRepository.save(new GiftImage(gift, key, order));
        gift.setUpdatedBy(currentUser());
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse deleteImage(UUID giftId, UUID imageId) {
        Gift gift = getEntity(giftId);
        GiftImage image = giftImageRepository.findById(imageId).orElseThrow(() -> new GiftImageNotFoundException(imageId));
        if (!image.getGift().getId().equals(giftId)) {
            throw new GiftImageNotFoundException(imageId);
        }
        giftImageRepository.delete(image);
        fileStorageService.delete(image.getImageKey());
        gift.setUpdatedBy(currentUser());
        return toResponse(gift);
    }

    private Gift getEntity(UUID id) {
        return giftRepository.findById(id).orElseThrow(() -> new GiftNotFoundException(id));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    private GiftResponse toResponse(Gift gift) {
        List<GiftImageResponse> images = giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(gift.getId()).stream()
                .map(image -> new GiftImageResponse(image.getId(), fileStorageService.publicUrl(image.getImageKey()), image.getDisplayOrder()))
                .toList();
        return GiftResponse.from(gift, images);
    }
}
