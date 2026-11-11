package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wrapzzo.backend.dto.BulkDoctorRow;
import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.ImportJobResponse;
import com.wrapzzo.backend.exception.ImportJobNotFoundException;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

/** Bulk uploads: a small sheet is done in the request, a large one is checked in the request and saved by a worker. */
@SpringBootTest
@Import(TestEmailSenderConfig.class)
@Transactional
class ImportJobIntegrationTest {

    private static final int LARGE = ImportJobService.INLINE_LIMIT + 1;

    @Autowired private ImportJobService importJobs;
    @Autowired private ImportJobStore store;
    @Autowired private UserService userService;
    @Autowired private CompanyRepository companyRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private jakarta.persistence.EntityManager entityManager;

    private Company company;
    private Role member;
    private User platformStaff;
    private User mr;
    private final String tag = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void seed() {
        company = companyRepository.save(new Company("Import Test Pharma " + tag));
        member = roleRepository.save(new Role(company, "Member"));
        Role platformRole = roleRepository.save(new Role(null, "Test Platform Staff " + tag));
        platformStaff = userRepository.save(new User(null, platformRole, "Platform Staff", "platform-" + tag + "@wrapzzo.com", "n/a"));
        mr = userRepository.save(new User(company, member, "Medical Rep", "mr-" + tag + "@import-test.com", "n/a"));
        signInAs(platformStaff);
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private void signInAs(User user) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(user.getId(), null, List.of()));
    }

    private List<BulkImportUserRow> userRows(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new BulkImportUserRow("Person " + i, "p" + i + "-" + tag + "@import-test.com", "9000000" + i, "KAM", "Member", "E" + i, null, List.of("EAST")))
                .toList();
    }

    private List<BulkDoctorRow> doctorRows(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new BulkDoctorRow("Dr " + i, "98" + String.format("%08d", i), "PC-" + tag + "-" + i, null, null, null, null))
                .toList();
    }

    private int jobsRows() {
        return jdbc.queryForObject("SELECT count(*) FROM import_jobs", Integer.class);
    }

    @Test
    void aSmallUserSheetIsDoneInTheRequestAndNoJobIsQueued() {
        int before = jobsRows();

        ImportJobResponse response = importJobs.importUsers(company.getId(), userRows(3));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.counts()).containsEntry("created", 3).containsEntry("updated", 0);
        assertThat(userRepository.findByEmail("p0-" + tag + "@import-test.com")).isPresent();
        assertThat(jobsRows()).isEqualTo(before);
    }

    @Test
    void aLargeUserSheetIsQueuedThenSavedByTheWorkerAsTheUploader() {
        ImportJobResponse queued = importJobs.importUsers(company.getId(), userRows(LARGE));

        assertThat(queued.status()).isEqualTo("QUEUED");
        assertThat(queued.totalRows()).isEqualTo(LARGE);
        assertThat(userRepository.findByEmail("p0-" + tag + "@import-test.com")).isEmpty();
        assertThat(importJobs.get(queued.id()).status()).isEqualTo("QUEUED");

        // The worker has no signed-in user of its own: it must act as the uploader.
        SecurityContextHolder.clearContext();
        assertThat(new ImportJobDispatcher(store, importJobs).runNext()).isTrue();
        signInAs(platformStaff);

        ImportJobResponse done = importJobs.get(queued.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.counts()).containsEntry("created", LARGE).containsEntry("updated", 0);
        assertThat(userRepository.findByEmail("p" + (LARGE - 1) + "-" + tag + "@import-test.com")).isPresent();
        // The rows are personal data: gone once the job is over.
        assertThat(jdbc.queryForObject("SELECT payload FROM import_jobs WHERE id = ?", String.class, queued.id())).isNull();
        assertThat(new ImportJobDispatcher(store, importJobs).runNext()).isFalse();
    }

    @Test
    void aBadRowInALargeSheetIsReportedAtOnceAndNothingIsQueued() {
        List<BulkImportUserRow> rows = new ArrayList<>(userRows(LARGE));
        rows.set(5, new BulkImportUserRow("Person X", "px-" + tag + "@import-test.com", "9", "KAM", "No Such Role", "E9", null, List.of("EAST")));
        int before = jobsRows();

        assertThatThrownBy(() -> importJobs.importUsers(company.getId(), rows))
                .isInstanceOf(UserImportValidationException.class)
                .satisfies(e -> assertThat(((UserImportValidationException) e).getErrors()).anyMatch(error -> error.contains("No Such Role")));
        assertThat(jobsRows()).isEqualTo(before);
    }

    @Test
    void ifTheSheetStopsBeingValidWhileItWaitsTheImportFailsWithTheRowsAndSavesNothing() {
        ImportJobResponse queued = importJobs.importUsers(company.getId(), userRows(LARGE));
        member.setActive(false);
        roleRepository.saveAndFlush(member);

        new ImportJobDispatcher(store, importJobs).runNext();

        ImportJobResponse failed = importJobs.get(queued.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.message()).contains("Nothing was imported");
        assertThat(failed.errors()).isNotEmpty();
        assertThat(userRepository.findByEmail("p0-" + tag + "@import-test.com")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT payload FROM import_jobs WHERE id = ?", String.class, queued.id())).isNull();
    }

    @Test
    void aLargeDoctorSheetIsQueuedThenAddedToTheUploadersList() {
        signInAs(mr);

        ImportJobResponse queued = importJobs.importDoctors(doctorRows(LARGE));
        assertThat(queued.status()).isEqualTo("QUEUED");
        SecurityContextHolder.clearContext();
        new ImportJobDispatcher(store, importJobs).runNext();
        signInAs(mr);

        ImportJobResponse done = importJobs.get(queued.id());
        assertThat(done.status()).isEqualTo("COMPLETED");
        assertThat(done.counts()).containsEntry("created", LARGE).containsEntry("joined", 0).containsEntry("unchanged", 0);
        entityManager.flush(); // the last inserts are still batched in the session
        assertThat(jdbc.queryForObject("SELECT count(*) FROM recipient_assignments WHERE mr_user_id = ?", Integer.class, mr.getId())).isEqualTo(LARGE);
    }

    @Test
    void aSmallDoctorSheetIsDoneInTheRequest() {
        signInAs(mr);

        ImportJobResponse response = importJobs.importDoctors(doctorRows(2));

        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.counts()).containsEntry("created", 2);
    }

    @Test
    void onlyTheUploaderCanFollowAnImport() {
        ImportJobResponse queued = importJobs.importUsers(company.getId(), userRows(LARGE));

        signInAs(mr);

        assertThatThrownBy(() -> importJobs.get(queued.id())).isInstanceOf(ImportJobNotFoundException.class);
        assertThatThrownBy(() -> importJobs.get(UUID.randomUUID())).isInstanceOf(ImportJobNotFoundException.class);
    }

    @Test
    void theWorkerGivesTheCallersSignInBackWhenItIsDone() {
        importJobs.importUsers(company.getId(), userRows(LARGE));
        Authentication before = SecurityContextHolder.getContext().getAuthentication();

        new ImportJobDispatcher(store, importJobs).runNext();

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(before);
    }

    @Test
    void anImportWhoseWorkerDiedIsTakenAgainThenGivenUpOnAfterTheLastAttempt() {
        ImportJobResponse queued = importJobs.importUsers(company.getId(), userRows(LARGE));

        for (int attempt = 1; attempt <= ImportJobDispatcher.MAX_ATTEMPTS; attempt++) {
            // A worker claims it and dies without recording anything.
            assertThat(store.claimNext(ImportJobDispatcher.MAX_ATTEMPTS, Duration.ofMinutes(30))).isPresent();
            assertThat(store.claimNext(ImportJobDispatcher.MAX_ATTEMPTS, Duration.ofMinutes(30))).isEmpty(); // still leased
            jdbc.update("UPDATE import_jobs SET claimed_until = clock_timestamp() - interval '1 second' WHERE id = ?", queued.id());
        }

        assertThat(store.claimNext(ImportJobDispatcher.MAX_ATTEMPTS, Duration.ofMinutes(30))).isEmpty();
        ImportJobResponse failed = importJobs.get(queued.id());
        assertThat(failed.status()).isEqualTo("FAILED");
        assertThat(failed.message()).contains("nothing was imported");
        assertThat(jdbc.queryForObject("SELECT payload FROM import_jobs WHERE id = ?", String.class, queued.id())).isNull();
    }

    @Test
    void theCleanupJobClearsOnlyOldFinishedImports() {
        ImportJobResponse old = importJobs.importUsers(company.getId(), userRows(LARGE));
        ImportJobResponse recent = importJobs.importUsers(company.getId(), userRows(LARGE));
        ImportJobResponse waiting = importJobs.importUsers(company.getId(), userRows(LARGE));
        store.markCompleted(old.id(), "{}");
        store.markCompleted(recent.id(), "{}");
        jdbc.update("UPDATE import_jobs SET finished_at = clock_timestamp() - interval '10 days' WHERE id = ?", old.id());

        new ImportJobCleanupJob(store).run();

        assertThat(store.find(old.id())).isEmpty();
        assertThat(store.find(recent.id())).isPresent();
        assertThat(store.find(waiting.id())).isPresent();
    }
}
