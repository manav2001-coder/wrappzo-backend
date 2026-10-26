package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.exception.CannotDeleteSelfException;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.LastCompanyAdminException;
import com.wrapzzo.backend.exception.UserHasActivityException;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.exception.UserNotFoundException;
import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.OrgUnit;
import com.wrapzzo.backend.model.Role;
import com.wrapzzo.backend.model.RoleAccessControl;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.model.UserInviteToken;
import com.wrapzzo.backend.model.UserStatus;
import com.wrapzzo.backend.repository.AccessControlRepository;
import com.wrapzzo.backend.repository.CompanyRepository;
import com.wrapzzo.backend.repository.OrgUnitRepository;
import com.wrapzzo.backend.repository.RoleAccessControlRepository;
import com.wrapzzo.backend.repository.RoleRepository;
import com.wrapzzo.backend.repository.UserInviteTokenRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
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
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk employee onboarding from a client's roster sheet -- see BulkImportUserRow's doc
 * comment for the generic template shape. No per-designation Role mapping yet: every
 * imported user lands in one auto-created "Member" role, granted just enough to see the
 * campaigns they're in the audience of and submit recipients into them (campaign.submit)
 * -- until real Role management exists, Company Admin and Member are the only two roles
 * a company gets.
 */
@Service
public class UserService {

    private static final String DEFAULT_MEMBER_ROLE_NAME = "Member";
    private static final String COMPANY_ADMIN_ROLE_NAME = "Company Admin";

    /** Granted to every new company's auto-seeded "Member" role. */
    private static final List<String> DEFAULT_MEMBER_ACCESS_KEYS =
            List.of("campaign.submit", "quickgift.create", "dashboard.view", "recipient.manage", "order.design.review");

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final AccessControlRepository accessControlRepository;
    private final RoleAccessControlRepository roleAccessControlRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserInviteTokenRepository userInviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyAccessGuard companyAccessGuard;
    private final DesignationAccessService designationAccessService;
    private final long inviteTokenExpiryHours;
    private final String frontendBaseUrl;

    public UserService(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RoleRepository roleRepository,
            AccessControlRepository accessControlRepository,
            RoleAccessControlRepository roleAccessControlRepository,
            OrgUnitRepository orgUnitRepository,
            UserInviteTokenRepository userInviteTokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            CompanyAccessGuard companyAccessGuard,
            DesignationAccessService designationAccessService,
            @Value("${user.invite-token-expiry-hours}") long inviteTokenExpiryHours,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.accessControlRepository = accessControlRepository;
        this.roleAccessControlRepository = roleAccessControlRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.userInviteTokenRepository = userInviteTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.companyAccessGuard = companyAccessGuard;
        this.designationAccessService = designationAccessService;
        this.inviteTokenExpiryHours = inviteTokenExpiryHours;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Transactional
    public BulkImportUsersResponse bulkImport(UUID companyId, List<BulkImportUserRow> rows) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
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
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        return userRepository.findByCompanyId(companyId).stream().map(UserResponse::from).toList();
    }

    /**
     * Promotes a company user to that company's own "Company Admin" role -- distinct from
     * the platform Super Admin (Wrapzzo's own staff, company_id null). Every company gets a
     * Company Admin role auto-seeded at creation time (see CompanyService), so this only
     * ever reassigns an existing user onto it; it never creates the role itself.
     *
     * Also propagates Company Admin's current grants onto the promoted user's designation
     * (additive, see DesignationAccessService.grantAccessControls) -- so anyone else sharing
     * that same designation, now or hired later, picks up the same access without Super Admin
     * having to hand-configure that designation separately. This is a one-time snapshot, not a
     * live link: demoting this user back to Member does NOT revert it (see removeCompanyAdmin).
     *
     * The user's roleId is baked into their JWT at login (see JwtService), so this takes
     * effect on their next token refresh/login, not necessarily their very next request.
     */
    @Transactional
    public UserResponse makeCompanyAdmin(UUID companyId, UUID userId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new UserNotFoundException(userId);
        }
        Role companyAdminRole = roleRepository
                .findByCompanyIdAndName(companyId, COMPANY_ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Company Admin role missing for company " + companyId));

        user.setRole(companyAdminRole);
        userRepository.save(user);

        if (user.getDesignation() != null) {
            Set<UUID> companyAdminGrantIds = roleAccessControlRepository.findByRoleId(companyAdminRole.getId()).stream()
                    .map(rac -> rac.getAccessControl().getId())
                    .collect(Collectors.toSet());
            designationAccessService.grantAccessControls(companyId, user.getDesignation(), companyAdminGrantIds);
        }

        return UserResponse.from(user);
    }

    /**
     * Demotes a Company Admin back to Member -- e.g. someone was promoted by mistake.
     * Refuses to leave a company with zero admins; same JWT-staleness caveat as
     * makeCompanyAdmin applies to when this takes effect for the demoted user.
     */
    @Transactional
    public UserResponse removeCompanyAdmin(UUID companyId, UUID userId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new UserNotFoundException(userId);
        }
        Role companyAdminRole = roleRepository
                .findByCompanyIdAndName(companyId, COMPANY_ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Company Admin role missing for company " + companyId));
        if (userRepository.countByCompanyIdAndRoleId(companyId, companyAdminRole.getId()) <= 1) {
            throw new LastCompanyAdminException(companyId);
        }
        Role memberRole = roleRepository
                .findByCompanyIdAndName(companyId, DEFAULT_MEMBER_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Member role missing for company " + companyId));

        user.setRole(memberRole);
        userRepository.save(user);
        return UserResponse.from(user);
    }

    /**
     * Hard-deletes a user added by mistake. Refuses (409, via the FK constraint itself) if
     * they have any campaign/submission/order history referencing them -- that history must
     * stay intact, so such a user can't be removed this way at all today.
     */
    @Transactional
    public void deleteUser(UUID companyId, UUID userId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        if (userId.equals(CurrentUser.id())) {
            throw new CannotDeleteSelfException();
        }
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new UserNotFoundException(userId);
        }
        try {
            userInviteTokenRepository.deleteUnusedByUserId(userId);
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new UserHasActivityException(userId);
        }
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

        // Same idea as the email checks above -- catch a duplicate employee code as a
        // specific, actionable row-level error here, instead of letting it reach the DB's
        // partial unique index and come back as a generic "conflicts with existing data".
        Map<String, String> employeeCodeOwnerEmail = new HashMap<>();
        for (User existing : userRepository.findByCompanyIdAndEmployeeCodeIsNotNull(companyId)) {
            employeeCodeOwnerEmail.put(existing.getEmployeeCode(), existing.getEmail());
        }
        for (int i = 0; i < rows.size(); i++) {
            BulkImportUserRow row = rows.get(i);
            String code = blankToNull(row.employeeCode());
            if (code == null) {
                continue;
            }
            String email = normalizedEmail(row.email());
            String existingOwner = employeeCodeOwnerEmail.get(code);
            if (existingOwner != null && !existingOwner.equals(email)) {
                errors.add("Row " + (i + 1) + " (" + row.email() + "): employee code '" + code + "' is already used by " + existingOwner);
                continue;
            }
            String previousOwnerInBatch = employeeCodeOwnerEmail.put(code, email);
            if (previousOwnerInBatch != null && !previousOwnerInBatch.equals(email)) {
                errors.add("Row " + (i + 1) + " (" + row.email() + "): employee code '" + code
                        + "' is also used by " + previousOwnerInBatch + " in this sheet");
            }
        }

        return errors;
    }

    private Role findOrCreateMemberRole(Company company) {
        return roleRepository.findByCompanyId(company.getId()).stream()
                .filter(role -> role.getName().equals(DEFAULT_MEMBER_ROLE_NAME))
                .findFirst()
                .orElseGet(() -> {
                    Role role = roleRepository.save(new Role(company, DEFAULT_MEMBER_ROLE_NAME));
                    for (String key : DEFAULT_MEMBER_ACCESS_KEYS) {
                        AccessControl accessControl = accessControlRepository
                                .findByKey(key)
                                .orElseThrow(() -> new IllegalStateException("Missing seeded access control: " + key));
                        roleAccessControlRepository.save(new RoleAccessControl(role, accessControl));
                    }
                    return role;
                });
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
     * The DB side stays batched and synchronous (see UserInviteTokenRepository's own doc
     * comment for why) -- the tokens have to exist before this returns. The actual emails
     * don't: they're dispatched via an event so UserInviteEmailListener can send them
     * asynchronously, after this transaction commits, off the request thread. Sending is
     * one HTTP call per recipient (Resend has no batch-send endpoint) -- for a
     * thousands-of-rows import that's the one cost genuinely worth not making the caller
     * wait on.
     */
    private void issueInvitesAndSendEmails(List<User> users) {
        if (users.isEmpty()) {
            return;
        }
        userInviteTokenRepository.deleteUnusedByUserIdIn(users.stream().map(User::getId).toList());

        Instant expiresAt = Instant.now().plus(Duration.ofHours(inviteTokenExpiryHours));
        List<UserInviteToken> tokens = new ArrayList<>(users.size());
        List<PendingInvite> pendingInvites = new ArrayList<>(users.size());
        for (User user : users) {
            String rawToken = TokenHasher.generateOpaqueToken();
            tokens.add(new UserInviteToken(user, TokenHasher.sha256Hex(rawToken), expiresAt));
            String setupUrl = frontendBaseUrl + "/setup-password?token=" + rawToken;
            pendingInvites.add(new PendingInvite(user.getEmail(), user.getName(), setupUrl, inviteTokenExpiryHours));
        }
        userInviteTokenRepository.saveAll(tokens);

        eventPublisher.publishEvent(new UserInvitesIssuedEvent(pendingInvites));
    }

    private String normalizedEmail(String email) {
        return email.trim().toLowerCase();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
