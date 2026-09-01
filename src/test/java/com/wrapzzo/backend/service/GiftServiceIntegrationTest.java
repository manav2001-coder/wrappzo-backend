package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateGiftRequest;
import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.dto.PresignGiftImageUploadResponse;
import com.wrapzzo.backend.dto.UpdateGiftRequest;
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.exception.VendorGiftEditNotAllowedException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/**
 * GiftService reads the caller from SecurityContextHolder (via CurrentUser),
 * which JwtAuthenticationFilter normally populates -- here we set it up
 * manually to exercise the service directly, the same way
 * AuthServiceIntegrationTest exercises AuthService directly.
 */
@SpringBootTest
@Transactional
class GiftServiceIntegrationTest {

    @Autowired
    private GiftService giftService;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private VendorRepository vendorRepository;

    private User adminUser;

    @BeforeEach
    void actAsAUser() {
        Company company = companyRepository.save(new Company("Gift Catalogue Test Co"));
        Role role = roleRepository.save(new Role(company, "Whatever Role"));
        adminUser = userRepository.save(new User(company, role, "Test Admin", "gift-test-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(adminUser.getId(), null, List.of()));
    }

    private Vendor saveVendor(String name) {
        return vendorRepository.save(new Vendor(name, name.toLowerCase().replace(" ", "-") + "-" + UUID.randomUUID() + "@example.com", null, "n/a", adminUser));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAndGetRoundTrip() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Steel Water Bottle", "Drinkware", "750ml", 50, 10, BigDecimal.valueOf(100), null, null, false));

        assertThat(created.active()).isTrue();
        assertThat(created.images()).isEmpty();
        // Regression check: giftNo is DB-generated (@Generated INSERT) and only readable
        // immediately if the service flushes rather than deferring the insert to commit.
        assertThat(created.displayId()).matches("GID-\\d+");

        GiftResponse fetched = giftService.get(created.id());
        assertThat(fetched.name()).isEqualTo("Steel Water Bottle");
        assertThat(fetched.category()).isEqualTo("Drinkware");
    }

    @Test
    void updateChangesFields() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Mug", "Drinkware", null, null, null, BigDecimal.valueOf(100), null, null, false));

        GiftResponse updated = giftService.update(created.id(), new UpdateGiftRequest("Ceramic Mug", "Drinkware", "350ml", 25, 7, BigDecimal.valueOf(100), null, null, false));

        assertThat(updated.name()).isEqualTo("Ceramic Mug");
        assertThat(updated.moq()).isEqualTo(25);
    }

    @Test
    void deactivatedGiftsAreHiddenFromTheBrowseViewButVisibleToAdmin() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Notebook", "Stationery", null, null, null, BigDecimal.valueOf(100), null, null, false));
        assertThat(giftService.listActive()).extracting(GiftResponse::id).contains(created.id());

        giftService.setActive(created.id(), false);

        assertThat(giftService.listActive()).extracting(GiftResponse::id).doesNotContain(created.id());
        assertThat(giftService.get(created.id()).active()).isFalse();
        assertThatThrownBy(() -> giftService.getActive(created.id())).isInstanceOf(GiftNotFoundException.class);
    }

    @Test
    void imageUploadRoundTripsThroughStorage() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Pen Set", "Stationery", null, null, null, BigDecimal.valueOf(100), null, null, false));

        PresignGiftImageUploadResponse presigned = giftService.presignImageUpload(created.id(), "image/png");
        assertThat(presigned.key()).startsWith("gifts/" + created.id() + "/image-").endsWith(".png");

        GiftResponse withImage = giftService.attachImage(created.id(), presigned.key(), 0);
        assertThat(withImage.images()).hasSize(1);
        assertThat(withImage.images().get(0).imageUrl()).contains(presigned.key());

        GiftResponse afterDelete = giftService.deleteImage(created.id(), withImage.images().get(0).id());
        assertThat(afterDelete.images()).isEmpty();
    }

    @Test
    void vendorCreatedGiftIsScopedToThatVendorAndVisibleToAdmin() {
        Vendor vendor = saveVendor("Acme Printing");

        GiftResponse created = giftService.createForVendor(
                new CreateGiftRequest("Vendor Tote Bag", "Bags", null, 20, 5, BigDecimal.valueOf(100), null, null, false), vendor.getId());

        assertThat(created.vendorId()).isEqualTo(vendor.getId());
        assertThat(created.vendorName()).isEqualTo("Acme Printing");
        assertThat(created.active()).isTrue();

        assertThat(giftService.listForVendor(vendor.getId())).extracting(GiftResponse::id).contains(created.id());
        // The admin-facing list/view surfaces vendor-submitted gifts too.
        assertThat(giftService.list()).extracting(GiftResponse::id).contains(created.id());
        assertThat(giftService.get(created.id()).vendorId()).isEqualTo(vendor.getId());
    }

    @Test
    void aVendorCannotSeeOrEditAnotherVendorsGift() {
        Vendor owner = saveVendor("Owner Vendor");
        Vendor other = saveVendor("Other Vendor");
        GiftResponse created = giftService.createForVendor(new CreateGiftRequest("Owner's Mug", "Drinkware", null, null, null, BigDecimal.valueOf(100), null, null, false), owner.getId());

        assertThatThrownBy(() -> giftService.getForVendor(created.id(), other.getId())).isInstanceOf(GiftNotFoundException.class);
        assertThatThrownBy(() ->
                        giftService.updateForVendor(created.id(), new UpdateGiftRequest("Hijacked", "Drinkware", null, null, null, BigDecimal.valueOf(100), null, null, false), other.getId()))
                .isInstanceOf(GiftNotFoundException.class);
    }

    @Test
    void adminCanDelistButNotEditAVendorGift() {
        Vendor vendor = saveVendor("Delist Test Vendor");
        GiftResponse created = giftService.createForVendor(new CreateGiftRequest("Delistable Gift", "Misc", null, null, null, BigDecimal.valueOf(100), null, null, false), vendor.getId());

        assertThatThrownBy(() ->
                        giftService.update(created.id(), new UpdateGiftRequest("Renamed", "Misc", null, null, null, BigDecimal.valueOf(100), null, null, false)))
                .isInstanceOf(VendorGiftEditNotAllowedException.class);

        GiftResponse delisted = giftService.setActive(created.id(), false);
        assertThat(delisted.active()).isFalse();
        assertThat(giftService.listForVendor(vendor.getId()).get(0).active()).isFalse();
    }

    @Test
    void vendorCanUpdateAndUploadImagesForTheirOwnGift() {
        Vendor vendor = saveVendor("Image Test Vendor");
        GiftResponse created = giftService.createForVendor(new CreateGiftRequest("Image Test Gift", "Misc", null, null, null, BigDecimal.valueOf(100), null, null, false), vendor.getId());

        GiftResponse updated = giftService.updateForVendor(
                created.id(), new UpdateGiftRequest("Renamed Gift", "Misc", "desc", 10, 5, BigDecimal.valueOf(100), null, null, false), vendor.getId());
        assertThat(updated.name()).isEqualTo("Renamed Gift");

        PresignGiftImageUploadResponse presigned = giftService.presignImageUploadForVendor(created.id(), vendor.getId(), "image/png");
        GiftResponse withImage = giftService.attachImageForVendor(created.id(), vendor.getId(), presigned.key(), 0);
        assertThat(withImage.images()).hasSize(1);

        GiftResponse afterDelete = giftService.deleteImageForVendor(created.id(), vendor.getId(), withImage.images().get(0).id());
        assertThat(afterDelete.images()).isEmpty();
    }
}
