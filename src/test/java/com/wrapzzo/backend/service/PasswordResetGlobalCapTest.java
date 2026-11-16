package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

/** The cap on reset emails across everyone in a day: past it, nobody is sent a link, so a flood cannot use up the sending allowance. */
@SpringBootTest
@TestPropertySource(properties = "auth.password-reset.global-daily-cap=2")
@Transactional
@RecordApplicationEvents
class PasswordResetGlobalCapTest {

    @Autowired private PasswordResetService users;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private ApplicationEvents events;

    @Test
    void onceTheDaysCapIsReachedNoMoreLinksAreSent() {
        Company company = companyRepository.save(new Company("Cap Test Co"));
        Role role = roleRepository.save(new Role(company, "AM"));
        long alreadyToday = events.stream(PasswordResetRequestedEvent.class).count();
        assertThat(alreadyToday).isZero();
        for (int i = 0; i < 4; i++) {
            User person = userRepository.save(new User(company, role, "Person " + i, "cap-" + i + "-" + UUID.randomUUID() + "@test.com", "n/a"));
            users.requestReset(person.getEmail());
        }
        // Other tests' rows are rolled back, so only these four could count: the first two get a link, the rest do not.
        assertThat(events.stream(PasswordResetRequestedEvent.class).count()).isLessThanOrEqualTo(2);
    }
}
