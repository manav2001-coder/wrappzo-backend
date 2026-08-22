package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserInviteToken;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk employee onboarding from a client's roster sheet -- see BulkImportUserRow's doc
 * comment for the generic template shape. No per-designation Role mapping yet: every
 * imported user lands in one auto-created "Member" role with no elevated access, until
 * real Role management exists.
 */
@Service
public class UserService {

    private static final String DEFAULT_MEMBER_ROLE_NAME = "Member";

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserInviteTokenRepository userInviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender;
    private final long inviteTokenExpiryHours;
    private final String frontendBaseUrl;

    public UserService(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RoleRepository roleRepository,
            OrgUnitRepository orgUnitRepository,
            UserInviteTokenRepository userInviteTokenRepository,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            @Value("${user.invite-token-expiry-hours}") long inviteTokenExpiryHours,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.userInviteTokenRepository = userInviteTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.inviteTokenExpiryHours = inviteTokenExpiryHours;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public BulkImportUsersResponse bulkImport(UUID companyId, List<BulkImportUserRow> rows) {
        Company company = companyRepository.findById(companyId).orElseThrow(() -> new CompanyNotFoundException(companyId));

        // One query for every email this batch could possibly need -- each row's own email,
        // plus every reportsTo email -- instead of a findByEmail per row. At thousands of rows
        // that difference is the whole ballgame: one round-trip vs. thousands of them.
        Set<String> relevantEmails = new HashSet<>();
        for (BulkImportUserRow row : rows) {
            relevantEmails.add(normalizedEmail(row.email()));
            String reportsToEmail = blankToNull(row.reportsToEmail());
            if (reportsToEmail != null) {
                relevantEmails.add(normalizedEmail(reportsToEmail));
            }
        }
        Map<String, User> byEmail = new HashMap<>();
        for (User existing : userRepository.findByEmailIn(relevantEmails)) {
            byEmail.put(existing.getEmail().toLowerCase(), existing);
        }

        List<String> errors = validate(companyId, rows, byEmail);
        if (!errors.isEmpty()) {
            throw new UserImportValidationException(errors);
        }

        Role memberRole = findOrCreateMemberRole(company);
        Map<String, OrgUnit> orgUnitCache = loadOrgUnitCache(company);

        // Argon2 is deliberately expensive (that's the point, for a real password) --
        // computed once per row it dominates a large batch's runtime for no security
        // benefit, since this placeholder is never actually checked: AuthService.login()
        // rejects an INVITED user on status alone, before any password comparison runs.
        // It's fully replaced by the user's own real hash once they set one via the
        // invite link, so every new row in this batch sharing it until then is safe.
        String placeholderHash = passwordEncoder.encode(TokenHasher.generateOpaqueToken());

        int createdCount = 0;
        int updatedCount = 0;
        List<User> toSave = new ArrayList<>(rows.size());

        for (BulkImportUserRow row : rows) {
            String email = normalizedEmail(row.email());
            OrgUnit orgUnit = resolveOrgUnit(company, row.orgUnitPath(), orgUnitCache);

            User user = byEmail.get(email);
            if (user == null) {
                user = new User(company, memberRole, row.name(), email, placeholderHash);
                user.setStatus(UserStatus.INVITED);
                createdCount++;
            } else {
                updatedCount++;
            }
            user.setName(row.name());
            user.setMobile(blankToNull(row.mobile()));
            user.setDesignation(blankToNull(row.designation()));
            user.setEmployeeCode(blankToNull(row.employeeCode()));
            user.setOrgUnit(orgUnit);
            byEmail.put(email, user);
            toSave.add(user);
        }
        // Batched (hibernate.jdbc.batch_size in application.properties) rather than one
        // insert/update round-trip per row -- ids are client-generated (GenerationType.UUID),
        // so nothing here needs a round-trip back for a DB-assigned key to allow batching.
        // Flushed immediately, deliberately: the invite-issuing step below runs a
        // @Modifying bulk delete, which auto-flushes the whole pending session before it
        // runs -- leaving thousands of these inserts unflushed until then would turn that
        // one delete into the point where all of this batch's write cost actually lands.
        userRepository.saveAll(toSave);
        userRepository.flush();

        for (BulkImportUserRow row : rows) {
            String reportsToEmail = blankToNull(row.reportsToEmail());
            if (reportsToEmail == null) {
                continue;
            }
            // Already resolved into byEmail above (either from the batch fetch or this same
            // run's own rows) -- no per-row lookup needed here either.
            User manager = byEmail.get(normalizedEmail(reportsToEmail));
            byEmail.get(normalizedEmail(row.email())).setReportsTo(manager);
        }

        List<User> toInvite = toSave.stream().filter(user -> user.getStatus() == UserStatus.INVITED).toList();
        issueInvitesAndSendEmails(toInvite);

        List<UserResponse> responses = rows.stream().map(row -> UserResponse.from(byEmail.get(normalizedEmail(row.email())))).toList();
        return new BulkImportUsersResponse(createdCount, updatedCount, responses);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByCompany(UUID companyId) {
        return userRepository.findByCompanyId(companyId).stream().map(UserResponse::from).toList();
    }

    /**
     * All rows are checked before any write happens -- either the whole sheet imports
     * cleanly, or none of it does, so a bad row never leaves a half-imported roster.
     * existingByEmail is the one bulk-fetched lookup from bulkImport -- no query here.
     */
    private List<String> validate(UUID companyId, List<BulkImportUserRow> rows, Map<String, User> existingByEmail) {
        List<String> errors = new ArrayList<>();
        Set<String> emailsInBatch = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkImportUserRow row = rows.get(i);
            String email = normalizedEmail(row.email());
            int rowNum = i + 1;

            if (!emailsInBatch.add(email)) {
                errors.add("Row " + rowNum + " (" + row.email() + "): duplicate email within this sheet");
            }
            User existing = existingByEmail.get(email);
            if (existing != null && !existing.getCompany().getId().equals(companyId)) {
                errors.add("Row " + rowNum + " (" + row.email() + "): this email already belongs to a different company");
            }
        }

        for (int i = 0; i < rows.size(); i++) {
            BulkImportUserRow row = rows.get(i);
            String reportsToEmail = blankToNull(row.reportsToEmail());
            if (reportsToEmail == null) {
                continue;
            }
            String normalized = normalizedEmail(reportsToEmail);
            boolean inBatch = emailsInBatch.contains(normalized);
            boolean inDb = existingByEmail.containsKey(normalized);
            if (!inBatch && !inDb) {
                errors.add("Row " + (i + 1) + " (" + row.email() + "): reportsTo email '" + reportsToEmail
                        + "' was not found in this sheet or among existing users");
            }
        }

        return errors;
    }

    private Role findOrCreateMemberRole(Company company) {
        return roleRepository.findByCompanyId(company.getId()).stream()
                .filter(role -> role.getName().equals(DEFAULT_MEMBER_ROLE_NAME))
                .findFirst()
                .orElseGet(() -> roleRepository.save(new Role(company, DEFAULT_MEMBER_ROLE_NAME)));
    }

    private Map<String, OrgUnit> loadOrgUnitCache(Company company) {
        Map<String, OrgUnit> cache = new HashMap<>();
        for (OrgUnit unit : orgUnitRepository.findByCompanyId(company.getId())) {
            cache.put(orgUnitCacheKey(unit.getParent() == null ? null : unit.getParent().getId(), unit.getName()), unit);
        }
        return cache;
    }

    /** Broad-to-narrow path (e.g. ["HOSPIAID", "EAST"]) -- shared branches are reused, not duplicated. */
    private OrgUnit resolveOrgUnit(Company company, List<String> path, Map<String, OrgUnit> cache) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        OrgUnit parent = null;
        OrgUnit current = null;
        for (int level = 0; level < path.size(); level++) {
            String name = blankToNull(path.get(level));
            if (name == null) {
                break;
            }
            String key = orgUnitCacheKey(parent == null ? null : parent.getId(), name);
            current = cache.get(key);
            if (current == null) {
                current = orgUnitRepository.save(new OrgUnit(company, parent, name, "Level " + (level + 1)));
                cache.put(key, current);
            }
            parent = current;
        }
        return current;
    }

    private String orgUnitCacheKey(UUID parentId, String name) {
        return (parentId == null ? "ROOT" : parentId.toString()) + "::" + name.trim().toLowerCase();
    }

    /**
     * Batched on purpose: a @Modifying query auto-flushes the entire pending session
     * before it runs, so doing the delete-then-save per user inside a loop turned every
     * other pending write in that session into repeated, ever-larger flush work -- one
     * bulk delete + one batched saveAll instead, regardless of how many users this is.
     */
    private void issueInvitesAndSendEmails(List<User> users) {
        if (users.isEmpty()) {
            return;
        }
        userInviteTokenRepository.deleteUnusedByUserIdIn(users.stream().map(User::getId).toList());

        Instant expiresAt = Instant.now().plus(Duration.ofHours(inviteTokenExpiryHours));
        List<UserInviteToken> tokens = new ArrayList<>(users.size());
        Map<UUID, String> rawTokenByUserId = new HashMap<>();
        for (User user : users) {
            String rawToken = TokenHasher.generateOpaqueToken();
            rawTokenByUserId.put(user.getId(), rawToken);
            tokens.add(new UserInviteToken(user, TokenHasher.sha256Hex(rawToken), expiresAt));
        }
        userInviteTokenRepository.saveAll(tokens);

        // Still one HTTP call per recipient -- Resend has no batch-send endpoint, so this
        // remains the one genuinely per-user cost left for a real (non-test) import of
        // thousands of rows. Worth revisiting (background job / queue) if that matters in
        // practice; not building that ahead of being asked for it.
        for (User user : users) {
            String setupUrl = frontendBaseUrl + "/setup-password?token=" + rawTokenByUserId.get(user.getId());
            emailSender.send(
                    user.getEmail(),
                    "Set up your Wrapzzo account",
                    """
                    <p>Hi %s,</p>
                    <p>You've been added on Wrapzzo. Set up your password to get started:</p>
                    <p><a href="%s">Set up your password</a></p>
                    <p>This link expires in %d hours.</p>
                    """
                            .formatted(user.getName(), setupUrl, inviteTokenExpiryHours));
        }
    }

    private String normalizedEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
