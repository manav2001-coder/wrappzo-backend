package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.dto.ProfileResponse;
import com.wrapzzo.backend.dto.VendorProfileResponse;
import com.wrapzzo.backend.exception.InvalidCredentialsException;
import com.wrapzzo.backend.exception.InvalidProfilePhotoException;
import com.wrapzzo.backend.exception.InvalidRefreshTokenException;
import com.wrapzzo.backend.exception.PasswordMismatchException;
import com.wrapzzo.backend.exception.WeakPasswordException;
import com.wrapzzo.backend.exception.WrongCurrentPasswordException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * A person's and a vendor's own profile, against the real database: what they may change, that a password change signs out
 * every other device but not this one, and that a picture is only accepted when it is theirs, small and really an image.
 * Storage is a stand-in, so what is uploaded can be chosen per test.
 */
@SpringBootTest
@Transactional
class ProfileServiceIntegrationTest {

    private static final String PASSWORD = "the-old-password";

    @Autowired private ProfileService profileService;
    @Autowired private VendorProfileService vendorProfileService;
    @Autowired private AuthService authService;
    @Autowired private VendorAuthService vendorAuthService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VendorRepository vendorRepository;
    @MockitoBean private FileStorageService fileStorage;

    private User user;
    private Vendor vendor;

    @BeforeEach
    void seed() {
        Company company = companyRepository.save(new Company("Profile Test Co"));
        Role role = roleRepository.save(new Role(company, "AM"));
        user = new User(company, role, "Rohit Sharma", "rohit-" + UUID.randomUUID() + "@test.com", passwordEncoder.encode(PASSWORD));
        user.setMobile("9000000001");
        user = userRepository.save(user);
        vendor = vendorRepository.save(new Vendor("Sky Prints", "vendor-" + UUID.randomUUID() + "@test.com", "9111111111", passwordEncoder.encode(PASSWORD), user));
        when(fileStorage.publicUrl(any())).thenAnswer(call -> "https://files.test/" + call.getArgument(0));
        when(fileStorage.presignUpload(any(), any(), any())).thenReturn("https://upload.test/put");
    }

    @AfterEach
    void signOut() {
        SecurityContextHolder.clearContext();
    }

    private void asUser() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private void asVendor() {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(vendor.getId(), null, List.of()));
    }

    private void storeImage(String key, long size, int... head) {
        byte[] bytes = new byte[head.length];
        for (int i = 0; i < head.length; i++) {
            bytes[i] = (byte) head[i];
        }
        when(fileStorage.inspect(eq(key), any(Integer.class))).thenReturn(new FileStorageService.StoredObject(size, bytes));
    }

    // ---- a person ----

    @Test
    void theProfileShowsWhoTheyAreAndWhatTheyMayChange() {
        asUser();
        ProfileResponse profile = profileService.get();
        assertThat(profile.name()).isEqualTo("Rohit Sharma");
        assertThat(profile.mobile()).isEqualTo("9000000001");
        assertThat(profile.roleName()).isEqualTo("AM");
        assertThat(profile.companyName()).isEqualTo("Profile Test Co");
        assertThat(profile.photoUrl()).isNull();
    }

    @Test
    void theyCanChangeTheirPhoneAndNothingElseOfTheirs() {
        asUser();
        ProfileResponse changed = profileService.updatePhone(" 9876543210 ");
        assertThat(changed.mobile()).isEqualTo("9876543210");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getName()).isEqualTo("Rohit Sharma");
    }

    @Test
    void aPasswordChangeSignsOutEveryOtherDeviceAndKeepsThisOne() {
        TokenPair phone = authService.login(user.getEmail(), PASSWORD);
        TokenPair laptop = authService.login(user.getEmail(), PASSWORD);
        asUser();

        TokenPair here = profileService.changePassword(PASSWORD, "a-brand-new-one", "a-brand-new-one");

        assertThatThrownBy(() -> authService.refresh(phone.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        assertThatThrownBy(() -> authService.refresh(laptop.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(authService.refresh(here.refreshToken())).isNotNull();
        assertThat(authService.login(user.getEmail(), "a-brand-new-one")).isNotNull();
        assertThatThrownBy(() -> authService.login(user.getEmail(), PASSWORD)).isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void aWrongCurrentPasswordChangesNothingAndCountsTowardsALockout() {
        asUser();
        assertThatThrownBy(() -> profileService.changePassword("not-my-password", "a-brand-new-one", "a-brand-new-one"))
                .isInstanceOf(WrongCurrentPasswordException.class).hasMessage("Your current password is not correct");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts()).isEqualTo((short) 1);
        assertThat(authService.login(user.getEmail(), PASSWORD)).isNotNull();
    }

    @Test
    void aNewPasswordThatDoesNotMatchOrIsWeakIsRefusedWithTheReason() {
        asUser();
        assertThatThrownBy(() -> profileService.changePassword(PASSWORD, "a-brand-new-one", "something-else")).isInstanceOf(PasswordMismatchException.class);
        assertThatThrownBy(() -> profileService.changePassword(PASSWORD, "short", "short")).isInstanceOf(WeakPasswordException.class).hasMessageContaining("at least 8");
        assertThatThrownBy(() -> profileService.changePassword(PASSWORD, PASSWORD, PASSWORD)).isInstanceOf(WeakPasswordException.class).hasMessageContaining("different");
    }

    @Test
    void aPictureIsAcceptedOnlyWhenItIsTheirOwnSmallAndReallyAnImage() {
        asUser();
        String mine = "users/" + user.getId() + "/profile-abc.png";
        storeImage(mine, 120_000, 0x89, 'P', 'N', 'G');
        assertThat(profileService.attachPhoto(mine).photoUrl()).isEqualTo("https://files.test/" + mine);

        assertThatThrownBy(() -> profileService.attachPhoto("users/" + UUID.randomUUID() + "/profile-abc.png")).isInstanceOf(InvalidProfilePhotoException.class).hasMessageContaining("not uploaded for your account");

        String big = "users/" + user.getId() + "/profile-big.png";
        storeImage(big, 3_000_000, 0x89, 'P', 'N', 'G');
        assertThatThrownBy(() -> profileService.attachPhoto(big)).isInstanceOf(InvalidProfilePhotoException.class).hasMessageContaining("too large");

        String fake = "users/" + user.getId() + "/profile-fake.png";
        storeImage(fake, 5_000, '%', 'P', 'D', 'F');
        assertThatThrownBy(() -> profileService.attachPhoto(fake)).isInstanceOf(InvalidProfilePhotoException.class).hasMessageContaining("JPG or PNG");

        String missing = "users/" + user.getId() + "/profile-missing.png";
        when(fileStorage.inspect(eq(missing), any(Integer.class))).thenReturn(null);
        assertThatThrownBy(() -> profileService.attachPhoto(missing)).isInstanceOf(InvalidProfilePhotoException.class).hasMessageContaining("not uploaded");
    }

    @Test
    void aNewPictureReplacesAndDeletesTheOldOneAndItCanBeRemoved() {
        asUser();
        String first = "users/" + user.getId() + "/profile-1.jpg";
        String second = "users/" + user.getId() + "/profile-2.jpg";
        storeImage(first, 1000, 0xFF, 0xD8, 0xFF, 0xE0);
        storeImage(second, 1000, 0xFF, 0xD8, 0xFF, 0xE0);
        profileService.attachPhoto(first);
        profileService.attachPhoto(second);
        verify(fileStorage).delete(first);

        assertThat(profileService.removePhoto().photoUrl()).isNull();
        verify(fileStorage).delete(second);
    }

    @Test
    void onlyAJpegOrPngIsOfferedAnUploadUrl() {
        asUser();
        assertThat(profileService.presignPhoto("image/png").key()).startsWith("users/" + user.getId() + "/profile-").endsWith(".png");
        assertThatThrownBy(() -> profileService.presignPhoto("application/pdf")).isInstanceOf(InvalidProfilePhotoException.class);
    }

    // ---- a vendor ----

    @Test
    void aVendorChangesTheirPhoneLogoAndPasswordButNotTheirName() {
        asVendor();
        VendorProfileResponse profile = vendorProfileService.get();
        assertThat(profile.name()).isEqualTo("Sky Prints");
        assertThat(vendorProfileService.updatePhone("9222222222").contactPhone()).isEqualTo("9222222222");
        assertThat(vendorRepository.findById(vendor.getId()).orElseThrow().getName()).isEqualTo("Sky Prints");

        String key = "vendors/" + vendor.getId() + "/profile-logo.png";
        storeImage(key, 90_000, 0x89, 'P', 'N', 'G');
        assertThat(vendorProfileService.attachPhoto(key).photoUrl()).isEqualTo("https://files.test/" + key);
        // A person's folder is not a vendor's.
        assertThatThrownBy(() -> vendorProfileService.attachPhoto("users/" + user.getId() + "/profile-x.png")).isInstanceOf(InvalidProfilePhotoException.class);
    }

    @Test
    void aVendorPasswordChangeSignsOutTheirOtherDevices() {
        TokenPair other = vendorAuthService.login(vendor.getContactEmail(), PASSWORD);
        asVendor();
        TokenPair here = vendorProfileService.changePassword(PASSWORD, "a-brand-new-one", "a-brand-new-one");

        assertThatThrownBy(() -> vendorAuthService.refresh(other.refreshToken())).isInstanceOf(InvalidRefreshTokenException.class);
        assertThat(vendorAuthService.refresh(here.refreshToken())).isNotNull();
        assertThatThrownBy(() -> vendorProfileService.changePassword("wrong", "another-new-one", "another-new-one")).isInstanceOf(WrongCurrentPasswordException.class);
    }
}
