package com.wrapzzo.backend.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Wrong passwords must be COUNTED and the account must lock. This test deliberately has no wrapping transaction: the count used to be
 * saved and then rolled back with the "invalid password" error, so a test that ran inside one transaction passed while the real app
 * never locked anyone. Here every request commits (or not) exactly as in production, and the rows are removed afterwards.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LoginLockoutHttpTest {

    private static final String PASSWORD = "correct-horse-battery-staple";

    @Autowired private MockMvc mvc;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private Company company;
    private Role role;
    private User user;

    @BeforeEach
    void setUp() {
        company = companyRepository.save(new Company("Lockout Pharma " + UUID.randomUUID()));
        role = roleRepository.save(new Role(company, "Member"));
        user = userRepository.save(new User(company, role, "Lock Me", "lock-" + UUID.randomUUID() + "@test.com", passwordEncoder.encode(PASSWORD)));
    }

    @AfterEach
    void tearDown() {
        userRepository.deleteById(user.getId());
        roleRepository.deleteById(role.getId());
        companyRepository.deleteById(company.getId());
    }

    private int login(String password) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse().getStatus();
    }

    @Test
    void wrongPasswordsAreCounted_andTheAccountLocksEvenAgainstTheRightPassword() throws Exception {
        for (int i = 1; i <= 5; i++) {
            assertThat(login("wrong-" + i)).isEqualTo(401);
        }

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getFailedLoginAttempts()).as("the count was kept, not rolled back with the error").isEqualTo((short) 5);
        assertThat(saved.getLockedUntil()).as("and the fifth locked the account").isNotNull();

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + user.getEmail() + "\",\"password\":\"" + PASSWORD + "\"}")).andExpect(status().isLocked());
    }

    @Test
    void aGoodPasswordSignsInAndClearsEarlierMistakes() throws Exception {
        login("wrong-1");
        login("wrong-2");
        assertThat(userRepository.findById(user.getId()).orElseThrow().getFailedLoginAttempts()).isEqualTo((short) 2);

        assertThat(login(PASSWORD)).isEqualTo(200);

        User saved = userRepository.findById(user.getId()).orElseThrow();
        assertThat(saved.getFailedLoginAttempts()).isZero();
        assertThat(saved.getLastActiveAt()).isNotNull();
    }
}
