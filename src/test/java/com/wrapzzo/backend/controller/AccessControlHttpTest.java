package com.wrapzzo.backend.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.repository.VendorRepository;
import com.wrapzzo.backend.security.JwtService;
import com.wrapzzo.backend.service.FeatureService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

/**
 * The server refuses, whatever the screens show: real requests through the whole security chain. Anyone not signed in is refused;
 * someone signed in without the key for an action is 403 -- reading or changing; and the company side and the vendor portal
 * cannot reach each other's endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessControlHttpTest {

    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private VendorRepository vendorRepository;
    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private JwtService jwtService;
    @Autowired private FeatureService featureService;

    private Company company;
    private final UUID anId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Access Test Co " + UUID.randomUUID()));
        featureService.registerAll(company);
    }

    /** A real token for a person whose role holds exactly these keys (none: signed in, and allowed nothing). */
    private RequestPostProcessor holding(String... keys) {
        Role only = roleRepository.save(new Role(company, "Role " + UUID.randomUUID()));
        User person = userRepository.save(new User(company, only, "Person", "p-" + UUID.randomUUID() + "@test.com", "n/a"));
        Template template = templateRepository.save(new Template(company, "Access " + UUID.randomUUID(), null));
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

    private RequestPostProcessor asVendor() {
        Vendor vendor = vendorRepository.save(new Vendor("Vendor " + UUID.randomUUID(), "v-" + UUID.randomUUID() + "@test.com", null, "n/a", null));
        String token = jwtService.generateVendorAccessToken(vendor);
        return request -> {
            request.addHeader("Authorization", "Bearer " + token);
            return request;
        };
    }

    private static final String NAME_BODY = "{\"name\":\"X\"}";

    @Test
    void nobodyNotSignedInGetsAnythingButTheOpenPages() throws Exception {
        // Refused outright (the server answers 403 to a request with no valid token).
        mvc.perform(get("/api/orders/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard")).andExpect(status().isForbidden());
        mvc.perform(get("/api/recipients/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/tbac/templates")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tbac/templates").contentType(MediaType.APPLICATION_JSON).content(NAME_BODY)).andExpect(status().isForbidden());
        mvc.perform(get("/api/vendor/orders/page")).andExpect(status().isForbidden());
        mvc.perform(get("/api/public/health")).andExpect(status().isOk());
    }

    @Test
    void aSignedInPersonWithNoKeysCannotReadWhatTheyHaveNoKeyFor() throws Exception {
        mvc.perform(get("/api/orders/page").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/summary").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard/export").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/recipients/page").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/quick-gifts/mine/page").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/order-approvals/pending/page").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/artwork-approvals/pending/page").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/tbac/templates").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/company/vendors").with(holding())).andExpect(status().isForbidden());
        mvc.perform(get("/api/platform/companies/list").with(holding())).andExpect(status().isForbidden());
    }

    @Test
    void aSignedInPersonWithNoKeysCannotChangeAnything() throws Exception {
        mvc.perform(post("/api/tbac/templates").with(holding()).contentType(MediaType.APPLICATION_JSON).content(NAME_BODY)).andExpect(status().isForbidden());
        mvc.perform(put("/api/tbac/templates/" + anId).with(holding()).contentType(MediaType.APPLICATION_JSON).content(NAME_BODY)).andExpect(status().isForbidden());
        mvc.perform(delete("/api/tbac/templates/" + anId).with(holding())).andExpect(status().isForbidden());
        mvc.perform(post("/api/tbac/templates/" + anId + "/clone").with(holding()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/tbac/templates/" + anId + "/make-editable").with(holding())).andExpect(status().isForbidden());
        mvc.perform(post("/api/tbac/templates/" + anId + "/features").with(holding()).contentType(MediaType.APPLICATION_JSON).content("{\"featureIds\":[\"" + anId + "\"]}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/order-approvals/" + anId + "/approve").with(holding()).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/order-approvals/" + anId + "/reject").with(holding()).contentType(MediaType.APPLICATION_JSON).content("{\"comment\":\"no\"}")).andExpect(status().isForbidden());
        mvc.perform(post("/api/platform/companies/add").with(holding()).contentType(MediaType.APPLICATION_JSON).content(NAME_BODY)).andExpect(status().isForbidden());
    }

    @Test
    void theKeyForOneThingDoesNotOpenAnother() throws Exception {
        // Reading orders is not reading the dashboard, and neither is changing a template.
        mvc.perform(get("/api/orders/page").with(holding("order.view"))).andExpect(status().isOk());
        mvc.perform(get("/api/dashboard").with(holding("order.view"))).andExpect(status().isForbidden());
        mvc.perform(post("/api/tbac/templates").with(holding("order.view")).contentType(MediaType.APPLICATION_JSON).content(NAME_BODY)).andExpect(status().isForbidden());
        // Seeing the clone button needs both rights: adding a template alone does not let someone grant features through it.
        mvc.perform(post("/api/tbac/templates/" + anId + "/clone").with(holding("template_master.add")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tbac/templates/" + anId + "/make-editable").with(holding("features_to_template.assign"))).andExpect(status().isForbidden());
    }

    @Test
    void theCompanySideAndTheVendorPortalCannotReachEachOther() throws Exception {
        mvc.perform(get("/api/vendor/orders/page").with(holding("order.view"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/orders/page").with(asVendor())).andExpect(status().isForbidden());
        mvc.perform(get("/api/dashboard").with(asVendor())).andExpect(status().isForbidden());
        mvc.perform(get("/api/tbac/templates").with(asVendor())).andExpect(status().isForbidden());
        mvc.perform(get("/api/vendor/orders/page").with(asVendor())).andExpect(status().isOk());
    }
}
