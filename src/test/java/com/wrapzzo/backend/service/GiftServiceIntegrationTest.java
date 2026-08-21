package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.CreateGiftRequest;
import com.wrapzzo.backend.dto.GiftResponse;
import com.wrapzzo.backend.dto.PresignGiftImageUploadResponse;
import com.wrapzzo.backend.dto.UpdateGiftRequest;
import com.wrapzzo.backend.exception.GiftNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
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

    @BeforeEach
    void actAsAUser() {
        Company company = companyRepository.save(new Company("Gift Catalogue Test Co"));
        Role role = roleRepository.save(new Role(company, "Whatever Role"));
        User user = userRepository.save(new User(company, role, "Test Admin", "gift-test-" + UUID.randomUUID() + "@wrapzzo.com", "n/a"));
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createAndGetRoundTrip() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Steel Water Bottle", "Drinkware", "750ml", 50, 10));

        assertThat(created.active()).isTrue();
        assertThat(created.images()).isEmpty();

        GiftResponse fetched = giftService.get(created.id());
        assertThat(fetched.name()).isEqualTo("Steel Water Bottle");
        assertThat(fetched.category()).isEqualTo("Drinkware");
    }

    @Test
    void updateChangesFields() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Mug", "Drinkware", null, null, null));

        GiftResponse updated = giftService.update(created.id(), new UpdateGiftRequest("Ceramic Mug", "Drinkware", "350ml", 25, 7));

        assertThat(updated.name()).isEqualTo("Ceramic Mug");
        assertThat(updated.moq()).isEqualTo(25);
    }

    @Test
    void deactivatedGiftsAreHiddenFromTheBrowseViewButVisibleToAdmin() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Notebook", "Stationery", null, null, null));
        assertThat(giftService.listActive()).extracting(GiftResponse::id).contains(created.id());

        giftService.setActive(created.id(), false);

        assertThat(giftService.listActive()).extracting(GiftResponse::id).doesNotContain(created.id());
        assertThat(giftService.get(created.id()).active()).isFalse();
        assertThatThrownBy(() -> giftService.getActive(created.id())).isInstanceOf(GiftNotFoundException.class);
    }

    @Test
    void imageUploadRoundTripsThroughStorage() {
        GiftResponse created = giftService.create(new CreateGiftRequest("Pen Set", "Stationery", null, null, null));

        PresignGiftImageUploadResponse presigned = giftService.presignImageUpload(created.id(), "image/png");
        assertThat(presigned.key()).startsWith("gifts/" + created.id() + "/image-").endsWith(".png");

        GiftResponse withImage = giftService.attachImage(created.id(), presigned.key(), 0);
        assertThat(withImage.images()).hasSize(1);
        assertThat(withImage.images().get(0).imageUrl()).contains(presigned.key());

        GiftResponse afterDelete = giftService.deleteImage(created.id(), withImage.images().get(0).id());
        assertThat(afterDelete.images()).isEmpty();
    }
}
