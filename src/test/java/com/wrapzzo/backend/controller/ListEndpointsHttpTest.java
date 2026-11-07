package com.wrapzzo.backend.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.List;
import com.wrapzzo.backend.model.Template;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.TemplateRepository;
import com.wrapzzo.backend.security.JwtService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/** The list endpoints over HTTP: the query string binds to a page request, the page comes back in the shape the screens read, and a bad request says why. */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ListEndpointsHttpTest {

    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;

    @Autowired private AccessControlRepository accessControlRepository;
    @Autowired private TemplateRepository templateRepository;
    @Autowired private JwtService jwtService;
    @Autowired private com.wrapzzo.backend.service.FeatureService featureService;

    private Company company;
    private Role role;
    private User user;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Http List Pharma " + UUID.randomUUID()));
        featureService.registerAll(company);
        role = roleRepository.save(new Role(company, "Member"));
        user = userRepository.save(new User(company, role, "Http User", "http-" + UUID.randomUUID() + "@test.com", "n/a"));
    }

    /** A real access token for the user, whose role holds exactly these keys -- so the whole security chain is what answers. */
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String... keys) {
        // A fresh role and person each time, so one request's keys never leak into the next.
        Role only = roleRepository.save(new Role(company, "Role " + UUID.randomUUID()));
        User person = userRepository.save(new User(company, only, "Http User", "http-" + UUID.randomUUID() + "@test.com", "n/a"));
        Template template = templateRepository.save(new Template(company, "Http access " + UUID.randomUUID(), null));
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

    @Test
    void ordersPageBindsTheQueryStringAndAnswersInThePageShape() throws Exception {
        mvc.perform(get("/api/orders/page")
                        .param("page", "0").param("size", "5").param("search", "asha").param("sort", "createdAt").param("dir", "desc")
                        .param("filters", "[{\"field\":\"status\",\"operator\":\"equals\",\"value\":\"DELIVERED\"}]")
                        .with(as("order.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(5))
                .andExpect(jsonPath("$.hasMore").value(false));
    }

    @Test
    void theSummaryAnswers() throws Exception {
        mvc.perform(get("/api/orders/summary").with(as("order.view")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.awaitingReview").value(0));
    }

    @Test
    void aBadFilterIsA400ThatSaysWhat() throws Exception {
        mvc.perform(get("/api/orders/page").param("filters", "[{\"field\":\"password\",\"operator\":\"equals\",\"value\":\"x\"}]").with(as("order.view")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("Cannot filter by 'password'")));
        mvc.perform(get("/api/orders/page").param("filters", "nonsense").with(as("order.view")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("filters could not be read")));
        mvc.perform(get("/api/orders/page").param("page", "-1").with(as("order.view")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void thePageEndpointsStillNeedTheirKeys() throws Exception {
        mvc.perform(get("/api/orders/page").with(as("doctor.view"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recipients/page").with(as("order.view"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/recipients/page").with(as("doctor.view"))).andExpect(status().isOk()).andExpect(jsonPath("$.total").value(0));
        mvc.perform(get("/api/quick-gifts/mine/page").with(as("quick_gift.create"))).andExpect(status().isOk());
        mvc.perform(get("/api/order-approvals/pending/page").with(as("approval.view"))).andExpect(status().isOk());
        mvc.perform(get("/api/artwork-approvals/pending/page").with(as("approval.view"))).andExpect(status().isOk());
        mvc.perform(get("/api/order-approvals/pending/page").with(as("order.view"))).andExpect(status().isForbidden());
    }
}
