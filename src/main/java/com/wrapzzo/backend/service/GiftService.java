package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateGiftRequest;
import com.wrapzzo.backend.dto.GiftImageResponse;
import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.dto.PresignGiftImageUploadResponse;
import com.wrapzzo.backend.dto.UpdateGiftRequest;
import com.wrapzzo.backend.exception.GiftImageNotFoundException;
import com.wrapzzo.backend.exception.GiftNameAlreadyExistsException;
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.exception.VendorGiftEditNotAllowedException;
import com.wrapzzo.backend.exception.VendorNotFoundException;
import com.wrapzzo.backend.model.Gift;
import com.wrapzzo.backend.model.GiftImage;
import com.wrapzzo.backend.model.GiftModule;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.GiftImageRepository;
import com.wrapzzo.backend.repository.GiftRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.EnumSet;
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

    /** Every gift is available in every commerce path unconditionally -- no per-gift picker. */
    private static final EnumSet<GiftModule> ALL_MODULES = EnumSet.allOf(GiftModule.class);

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/png", "png",
            "image/jpeg", "jpg",
            "image/jpg", "jpg",
            "image/webp", "webp",
            "image/svg+xml", "svg");

    private final GiftRepository giftRepository;
    private final GiftImageRepository giftImageRepository;
    private final UserRepository userRepository;
    private final VendorRepository vendorRepository;
    private final FileStorageService fileStorageService;

    @Transactional
    public GiftResponse create(CreateGiftRequest request) {
        if (giftRepository.findByName(request.name()).isPresent()) {
            throw new GiftNameAlreadyExistsException(request.name());
        }

        User creator = currentUser();
        Gift toSave = new Gift(request.name(), request.category(), request.description(), request.moq(), request.leadTimeDays(), creator);
        toSave.setModules(new java.util.HashSet<>(ALL_MODULES));
        toSave.setPrice(request.price());
        toSave.setMarkupFeePercentage(feeOrZero(request.markupFeePercentage()));
        toSave.setCommissionFeePercentage(feeOrZero(request.commissionFeePercentage()));
        // saveAndFlush -- giftNo is DB-generated and only readable after the physical
        // INSERT runs, which plain save() would otherwise defer until transaction commit.
        Gift gift = giftRepository.saveAndFlush(toSave);
        return toResponse(gift);
    }

    // readOnly transactions here (not just plain reads) -- toResponse() touches the lazy
    // vendor association, which throws LazyInitializationException without an open session.
    @Transactional(readOnly = true)
    public List<GiftResponse> list() {
        return giftRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<GiftResponse> listActive() {
        return giftRepository.findByActiveTrue().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GiftResponse get(UUID id) {
        return toResponse(getEntity(id));
    }

    @Transactional(readOnly = true)
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
        requireNotVendorOwned(gift);
        giftRepository.findByName(request.name()).filter(existing -> !existing.getId().equals(id)).ifPresent(existing -> {
            throw new GiftNameAlreadyExistsException(request.name());
        });
        gift.setName(request.name());
        gift.setCategory(request.category());
        gift.setDescription(request.description());
        gift.setMoq(request.moq());
        gift.setLeadTimeDays(request.leadTimeDays());
        gift.setPrice(request.price());
        gift.setMarkupFeePercentage(feeOrZero(request.markupFeePercentage()));
        gift.setCommissionFeePercentage(feeOrZero(request.commissionFeePercentage()));
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
        return presign(giftId, contentType);
    }

    @Transactional
    public GiftResponse attachImage(UUID giftId, String key, Integer displayOrder) {
        Gift gift = getEntity(giftId);
        requireNotVendorOwned(gift);
        int order = displayOrder != null ? displayOrder : giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(giftId).size();
        giftImageRepository.save(new GiftImage(gift, key, order));
        gift.setUpdatedBy(currentUser());
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse deleteImage(UUID giftId, UUID imageId) {
        Gift gift = getEntity(giftId);
        requireNotVendorOwned(gift);
        GiftImage image = giftImageRepository.findById(imageId).orElseThrow(() -> new GiftImageNotFoundException(imageId));
        if (!image.getGift().getId().equals(giftId)) {
            throw new GiftImageNotFoundException(imageId);
        }
        giftImageRepository.delete(image);
        fileStorageService.delete(image.getImageKey());
        gift.setUpdatedBy(currentUser());
        return toResponse(gift);
    }

    // --- Vendor self-service: a vendor manages only the gifts they submitted themselves. ---

    @Transactional
    public GiftResponse createForVendor(CreateGiftRequest request, UUID vendorId) {
        if (giftRepository.findByName(request.name()).isPresent()) {
            throw new GiftNameAlreadyExistsException(request.name());
        }

        Vendor vendor = getVendorEntity(vendorId);
        Gift toSave = new Gift(request.name(), request.category(), request.description(), request.moq(), request.leadTimeDays(), vendor);
        toSave.setModules(new java.util.HashSet<>(ALL_MODULES));
        toSave.setPrice(request.price());
        toSave.setMarkupFeePercentage(feeOrZero(request.markupFeePercentage()));
        toSave.setCommissionFeePercentage(feeOrZero(request.commissionFeePercentage()));
        // saveAndFlush -- giftNo is DB-generated and only readable after the physical
        // INSERT runs, which plain save() would otherwise defer until transaction commit.
        Gift gift = giftRepository.saveAndFlush(toSave);
        return toResponse(gift);
    }

    @Transactional(readOnly = true)
    public List<GiftResponse> listForVendor(UUID vendorId) {
        return giftRepository.findByVendorId(vendorId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public GiftResponse getForVendor(UUID giftId, UUID vendorId) {
        return toResponse(getOwnedByVendor(giftId, vendorId));
    }

    @Transactional
    public GiftResponse updateForVendor(UUID giftId, UpdateGiftRequest request, UUID vendorId) {
        Gift gift = getOwnedByVendor(giftId, vendorId);
        giftRepository.findByName(request.name()).filter(existing -> !existing.getId().equals(giftId)).ifPresent(existing -> {
            throw new GiftNameAlreadyExistsException(request.name());
        });
        gift.setName(request.name());
        gift.setCategory(request.category());
        gift.setDescription(request.description());
        gift.setMoq(request.moq());
        gift.setLeadTimeDays(request.leadTimeDays());
        gift.setPrice(request.price());
        gift.setMarkupFeePercentage(feeOrZero(request.markupFeePercentage()));
        gift.setCommissionFeePercentage(feeOrZero(request.commissionFeePercentage()));
        return toResponse(gift);
    }

    public PresignGiftImageUploadResponse presignImageUploadForVendor(UUID giftId, UUID vendorId, String contentType) {
        getOwnedByVendor(giftId, vendorId);
        return presign(giftId, contentType);
    }

    @Transactional
    public GiftResponse attachImageForVendor(UUID giftId, UUID vendorId, String key, Integer displayOrder) {
        Gift gift = getOwnedByVendor(giftId, vendorId);
        int order = displayOrder != null ? displayOrder : giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(giftId).size();
        giftImageRepository.save(new GiftImage(gift, key, order));
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse deleteImageForVendor(UUID giftId, UUID vendorId, UUID imageId) {
        Gift gift = getOwnedByVendor(giftId, vendorId);
        GiftImage image = giftImageRepository.findById(imageId).orElseThrow(() -> new GiftImageNotFoundException(imageId));
        if (!image.getGift().getId().equals(giftId)) {
            throw new GiftImageNotFoundException(imageId);
        }
        giftImageRepository.delete(image);
        fileStorageService.delete(image.getImageKey());
        return toResponse(gift);
    }

    private Gift getOwnedByVendor(UUID giftId, UUID vendorId) {
        Gift gift = getEntity(giftId);
        // 404, not 403 -- a vendor shouldn't be able to tell "not yours" apart from "doesn't exist".
        if (gift.getVendor() == null || !gift.getVendor().getId().equals(vendorId)) {
            throw new GiftNotFoundException(giftId);
        }
        return gift;
    }

    private void requireNotVendorOwned(Gift gift) {
        if (gift.getVendor() != null) {
            throw new VendorGiftEditNotAllowedException(gift.getId());
        }
    }

    private Vendor getVendorEntity(UUID id) {
        return vendorRepository.findById(id).orElseThrow(() -> new VendorNotFoundException(id));
    }

    private Gift getEntity(UUID id) {
        return giftRepository.findById(id).orElseThrow(() -> new GiftNotFoundException(id));
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }

    private BigDecimal feeOrZero(BigDecimal fee) {
        return fee != null ? fee : BigDecimal.ZERO;
    }

    private PresignGiftImageUploadResponse presign(UUID giftId, String contentType) {
        String extension = EXTENSION_BY_CONTENT_TYPE.get(contentType);
        String key = "gifts/%s/image-%s.%s".formatted(giftId, UUID.randomUUID(), extension);
        String uploadUrl = fileStorageService.presignUpload(key, contentType, IMAGE_UPLOAD_URL_EXPIRY);
        return new PresignGiftImageUploadResponse(uploadUrl, key);
    }

    private GiftResponse toResponse(Gift gift) {
        List<GiftImageResponse> images = giftImageRepository.findByGiftIdOrderByDisplayOrderAsc(gift.getId()).stream()
                .map(image -> new GiftImageResponse(image.getId(), fileStorageService.publicUrl(image.getImageKey()), image.getDisplayOrder()))
                .toList();
        return GiftResponse.from(gift, images);
    }
}
