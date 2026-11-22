package com.wrapzzo.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.JwtService;
import com.wrapzzo.backend.service.AuthService;
import com.wrapzzo.backend.service.FeatureService;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/** When people were last active: stamped at sign-in and renewal, and shown, sorted and filtered only for someone with platform_user_activity.view. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LastActiveHttpTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private JwtService jwtService;
    @Autowired private FeatureService featureService;
    @Autowired private AuthService authService;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private com.wrapzzo.backend.repository.VendorRepository vendorRepository;
    @Autowired private com.wrapzzo.backend.service.VendorAuthService vendorAuthService;

    private Company company;
    private Role memberRole;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Last Active Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        memberRole = roleRepository.save(new Role(company, "Member"));
    }

    private User person(String name, Instant lastActiveAt) {
        User user = new User(company, memberRole, name, name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@test.com", passwordEncoder.encode(PASSWORD));
        user.setLastActiveAt(lastActiveAt);
        return userRepository.save(user);
    }

    /** A company-wide viewer whose role holds exactly these keys. */
    private RequestPostProcessor viewerWith(String... keys) {
        Role only = roleRepository.save(new Role(company, "Viewer " + UUID.randomUUID()));
        User viewer = new User(company, only, "Viewer", "viewer-" + UUID.randomUUID() + "@test.com", "n/a");
        viewer.setCompanyWide(true);
        viewer = userRepository.save(viewer);
        Template template = templateRepository.save(new Template(company, "Viewer access " + UUID.randomUUID(), null));
        for (String key : keys) {
            template.getAccessControls().add(accessControlRepository.findByKey(key).orElseThrow());
        }
        templateRepository.save(template);
        only.getTemplates().add(template);
        roleRepository.save(only);
        String token = jwtService.generateAccessToken(viewer);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    /** Platform staff (the Super Admin): no company, and a role that holds exactly these keys -- the activity key is never a company's to hold. */
    private RequestPostProcessor platformStaffHolding(String... keys) {
        Role only = roleRepository.save(new Role(null, "Platform role " + UUID.randomUUID()));
        User person = userRepository.save(new User(null, only, "Super", "s-" + UUID.randomUUID() + "@test.com", "n/a"));
        Template template = templateRepository.save(new Template(null, "Platform access " + UUID.randomUUID(), null));
        for (String key : keys) {
            template.getAccessControls().add(accessControlRepository.findByKey(key).orElseThrow());
        }
        templateRepository.save(template);
        only.getTemplates().add(template);
        roleRepository.save(only);
        String token = jwtService.generateAccessToken(person);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private String usersPage() {
        return "/api/platform/companies/" + company.getId() + "/users/page";
    }

    @Test
    void signingInStampsWhenTheyWereLastActive_andRenewingTheSignInStampsItAgain() {
        User user = person("Asha Rao", null);
        assertThat(user.getLastActiveAt()).isNull();

        Instant before = Instant.now();
        var tokens = authService.login(user.getEmail(), PASSWORD);
        Instant afterLogin = user.getLastActiveAt();
        assertThat(afterLogin).isAfterOrEqualTo(before);

        user.setLastActiveAt(Instant.now().minus(Duration.ofHours(3)));
        authService.refresh(tokens.refreshToken());
        assertThat(user.getLastActiveAt()).isAfterOrEqualTo(afterLogin);
    }

    @Test
    void aSuperAdminSeesWhenEachPersonWasLastActive() throws Exception {
        person("Recent Rao", Instant.now().minus(Duration.ofHours(2)));
        mvc.perform(get(usersPage()).with(platformStaffHolding("user.view", "platform_user_activity.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='Recent Rao')].lastActiveAt").isNotEmpty());
    }

    @Test
    void everyoneElseNeverGetsTheTimeEvenThoughItIsStored() throws Exception {
        person("Hidden Rao", Instant.now());
        mvc.perform(get(usersPage()).with(viewerWith("user.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.name=='Hidden Rao')].lastActiveAt").value(org.hamcrest.Matchers.contains((Object) null)));
    }

    @Test
    void theActivityFilterAndSortAreNotOfferedWithoutThePermission() throws Exception {
        person("Some Rao", Instant.now());
        mvc.perform(get(usersPage()).param("filters", "[{\"field\":\"activity\",\"operator\":\"equals\",\"value\":\"never\"}]").with(viewerWith("user.view")))
                .andExpect(status().isBadRequest());
        mvc.perform(get(usersPage()).param("sort", "activity").with(viewerWith("user.view")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void theActivityPresetsPickTheRightPeople() throws Exception {
        person("Today Rao", Instant.now().minus(Duration.ofHours(1)));
        person("Lastweek Rao", Instant.now().minus(Duration.ofDays(5)));
        person("Stale Rao", Instant.now().minus(Duration.ofDays(10)));
        person("Gone Rao", Instant.now().minus(Duration.ofDays(45)));
        person("Never Rao", null);
        var viewer = platformStaffHolding("user.view", "platform_user_activity.view");

        assertNames("today", viewer, "Today Rao");
        assertNames("week", viewer, "Today Rao", "Lastweek Rao");
        assertNames("inactive7", viewer, "Stale Rao", "Gone Rao");
        assertNames("inactive30", viewer, "Gone Rao");
        assertNames("never", viewer, "Never Rao");
    }

    private void assertNames(String preset, RequestPostProcessor viewer, String... expected) throws Exception {
        var body = mvc.perform(get(usersPage()).param("size", "100")
                        .param("filters", "[{\"field\":\"activity\",\"operator\":\"equals\",\"value\":\"" + preset + "\"}]").with(viewer))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        for (String name : expected) {
            assertThat(body).as(preset).contains(name);
        }
        for (String other : new String[] {"Today Rao", "Lastweek Rao", "Stale Rao", "Gone Rao", "Never Rao"}) {
            if (!java.util.Arrays.asList(expected).contains(other)) {
                assertThat(body).as(preset + " excludes " + other).doesNotContain(other);
            }
        }
    }

    @Test
    void aSuperAdminCanSortByLastActive() throws Exception {
        person("Old Rao", Instant.now().minus(Duration.ofDays(9)));
        person("New Rao", Instant.now());
        mvc.perform(get(usersPage()).param("sort", "activity").param("dir", "desc").with(platformStaffHolding("user.view", "platform_user_activity.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("New Rao"));
    }

    private com.wrapzzo.backend.model.Vendor vendor(String name) {
        User creator = person("Vendor Creator", null);
        var vendor = new com.wrapzzo.backend.model.Vendor(name, name.toLowerCase().replace(' ', '.') + "-" + UUID.randomUUID() + "@test.com", null, passwordEncoder.encode(PASSWORD), creator);
        vendor.setLastActiveAt(Instant.now().minus(Duration.ofHours(5)));
        return vendorRepository.save(vendor);
    }

    @Test
    void aVendorSigningInIsStamped() {
        var vendor = vendor("Stamp Print");
        vendor.setLastActiveAt(null);
        Instant before = Instant.now();
        vendorAuthService.login(vendor.getContactEmail(), PASSWORD);
        assertThat(vendor.getLastActiveAt()).isAfterOrEqualTo(before);
    }

    @Test
    void vendorsShowWhenTheyWereLastActiveOnlyToSomeoneWhoMaySeeIt() throws Exception {
        vendor("Seen Print");
        mvc.perform(get("/api/platform/vendors/list").with(platformStaffHolding("platform_vendor.view", "platform_user_activity.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Seen Print')].lastActiveAt").isNotEmpty());
        mvc.perform(get("/api/platform/vendors/list").with(platformStaffHolding("platform_vendor.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='Seen Print')].lastActiveAt").value(org.hamcrest.Matchers.contains((Object) null)));
    }
}
