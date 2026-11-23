package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Recipient;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RecipientAssignmentRepository;
import com.wrapzzo.backend.repository.RecipientRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two requests putting the same doctor on the same MR's list at the same moment (a double click, the same file uploaded
 * twice) must both succeed and leave one row -- not one of them failing on the unique rule. Not wrapped in a test
 * transaction: the race only exists between separate, committed transactions.
 */
@SpringBootTest
class RecipientAssignmentRaceTest {

    @Autowired private RecipientAssignmentRepository assignments;
    @Autowired private RecipientRepository recipientRepository;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private PlatformTransactionManager txm;

    private Company company;

    @AfterEach
    void cleanUp() {
        if (company != null) {
            new TransactionTemplate(txm).executeWithoutResult(s -> {
                recipientRepository.findAll().stream().filter(r -> r.getCompany() != null && r.getCompany().getId().equals(company.getId()))
                        .forEach(recipientRepository::delete);
                userRepository.findAll().stream().filter(u -> u.getCompany() != null 
                        && u.getCompany().getId().equals(company.getId()))
                        .forEach(userRepository::delete);
                roleRepository.findAll().stream().filter(r -> r.getCompany() != null && r.getCompany().getId().equals(company.getId()))
                        .forEach(roleRepository::delete);
                companyRepository.deleteById(company.getId());
            });
        }
    }

    @Test
    void sameDoctorAddedToSameListAtOnceLeavesOneRowAndNoError() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(txm);
        UUID[] ids = tx.execute(s -> {
            company = companyRepository.save(new Company("Race Pharma " + UUID.randomUUID()));
            Role role = roleRepository.save(new Role(company, "AM"));
            User mr = userRepository.save(new User(company, role, "MR", "r-" + UUID.randomUUID() + "@test.com", "n/a"));
            Recipient doc = new Recipient(company, "Dr Race", "9000000000", null, null, mr);
            doc.setPcode("RACE-1");
            doc = recipientRepository.save(doc);
            return new UUID[] {doc.getId(), mr.getId()};
        });

        int threads = 8;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            Callable<Integer> add = () -> {
                go.await();
                return tx.execute(s -> assignments.addIfAbsent(ids[0], ids[1]));
            };
            results.add(pool.submit(add));
        }
        go.countDown();
        int added = 0;
        for (Future<Integer> f : results) {
            added += f.get(); // would throw if any request hit the unique rule
        }
        pool.shutdown();

        assertThat(added).isEqualTo(1);
        assertThat(assignments.findByMrUser_IdOrderByAssignedAtDesc(ids[1])).hasSize(1);
    }
}
