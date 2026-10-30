package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkImportUserRow;
import com.wrapzzo.backend.dto.BulkImportUsersResponse;
import com.wrapzzo.backend.dto.UserResponse;
import com.wrapzzo.backend.exception.CannotDeleteSelfException;
import com.wrapzzo.backend.exception.CompanyNotFoundException;
import com.wrapzzo.backend.exception.ConflictException;
import com.wrapzzo.backend.exception.LastCompanyAdminException;
import com.wrapzzo.backend.exception.OrgUnitNotFoundException;
import com.wrapzzo.backend.exception.RoleNotFoundException;
import com.wrapzzo.backend.exception.UserHasActivityException;
import com.wrapzzo.backend.exception.UserImportValidationException;
import com.wrapzzo.backend.exception.UserNotFoundException;
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
import com.wrapzzo.backend.security.CurrentUser;
import com.wrapzzo.backend.security.TokenHasher;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bulk employee onboarding from a client's roster sheet -- see BulkImportUserRow's doc
 * comment for the generic template shape. Every row names a role of the company (the roles
 * master, RoleService) -- what that role may do comes from the templates mapped to it.
 */
@Service
public class UserService {

    private static final String COMPANY_ADMIN_ROLE_NAME = RoleService.COMPANY_ADMIN_ROLE_NAME;

    private final UserRepository userRepository;
    private final CompanyRepository companyRepository;
    private final RoleRepository roleRepository;
    private final OrgUnitRepository orgUnitRepository;
    private final UserInviteTokenRepository userInviteTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher eventPublisher;
    private final CompanyAccessGuard companyAccessGuard;
    private final AccessControlCacheService accessControlCacheService;
    private final VisibilityService visibilityService;
    private final long inviteTokenExpiryHours;
    private final String frontendBaseUrl;

    public UserService(
            UserRepository userRepository,
            CompanyRepository companyRepository,
            RoleRepository roleRepository,
            OrgUnitRepository orgUnitRepository,
            UserInviteTokenRepository userInviteTokenRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            CompanyAccessGuard companyAccessGuard,
            AccessControlCacheService accessControlCacheService,
            VisibilityService visibilityService,
            @Value("${user.invite-token-expiry-hours}") long inviteTokenExpiryHours,
            @Value("${app.frontend-base-url}") String frontendBaseUrl) {
        this.userRepository = userRepository;
        this.companyRepository = companyRepository;
        this.roleRepository = roleRepository;
        this.orgUnitRepository = orgUnitRepository;
        this.userInviteTokenRepository = userInviteTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.companyAccessGuard = companyAccessGuard;
        this.accessControlCacheService = accessControlCacheService;
        this.visibilityService = visibilityService;
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

        Map<String, Role> rolesByName = new HashMap<>();
        for (Role role : roleRepository.findByCompanyId(companyId)) {
            if (role.isActive()) {
                rolesByName.put(role.getName().toLowerCase(), role);
            }
        }
        List<String> errors = validate(companyId, rows, byEmail, rolesByName);
        if (!errors.isEmpty()) {
            throw new UserImportValidationException(errors);
        }

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
        // Importing is user.create, but placing someone company-wide or moving an existing user
        // (the importer included) changes who sees what -- that takes placement authority, or a
        // re-import would be a way round setCompanyWide/setExtraOrgUnits.
        boolean placementAuthority = hasPlacementAuthority();

        for (BulkImportUserRow row : rows) {
            String email = normalizedEmail(row.email());
            boolean companyWide = isCompanyWidePlacement(row.orgUnitPath());
            OrgUnit orgUnit = companyWide ? null : resolveOrgUnit(company, row.orgUnitPath(), orgUnitCache);

            User user = byEmail.get(email);
            if (!placementAuthority && (companyWide || (user != null && movesPlacement(user, orgUnit)))) {
                throw new AccessDeniedException("Only someone who sees the whole company can place " + row.email()
                        + (companyWide ? " at the whole company" : " in a different org unit"));
            }
            Role role = rolesByName.get(row.role().trim().toLowerCase());
            if (user == null) {
                user = new User(company, role, row.name(), email, placeholderHash);
                user.setStatus(UserStatus.INVITED);
                createdCount++;
            } else {
                // A Company Admin is demoted through removeCompanyAdmin (which keeps the last one), never by a sheet.
                if (!COMPANY_ADMIN_ROLE_NAME.equals(user.getRole().getName())) {
                    user.setRole(role);
                }
                updatedCount++;
            }
            user.setName(row.name());
            user.setMobile(blankToNull(row.mobile()));
            user.setDesignation(blankToNull(row.designation()));
            user.setEmployeeCode(blankToNull(row.employeeCode()));
            user.setOrgUnit(orgUnit);
            user.setCompanyWide(companyWide);
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
        accessControlCacheService.invalidateAfterCommit();

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
        eventPublisher.publishEvent(new CompanyStructureChangedEvent(companyId));

        List<UserResponse> responses = rows.stream().map(row -> UserResponse.from(byEmail.get(normalizedEmail(row.email())))).toList();
        return new BulkImportUsersResponse(createdCount, updatedCount, responses);
    }

    @Transactional(readOnly = true)
    public List<UserResponse> listByCompany(UUID companyId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        Visibility visibility = visibilityService.resolve(currentUser());
        return userRepository
                .findVisibleInCompany(companyId, visibility.companyWide(), visibility.peopleForQuery(), visibility.orgUnitsForQuery())
                .stream()
                .map(UserResponse::from)
                .toList();
    }

    /**
     * Makes a user see the whole company (or narrows them back to their place in the tree).
     * Only someone who already sees the whole company may change this -- see requirePlacementAuthority.
     */
    @Transactional
    public UserResponse setCompanyWide(UUID companyId, UUID userId, boolean companyWide) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        requirePlacementAuthority();
        User user = getCompanyUser(companyId, userId);
        user.setCompanyWide(companyWide);
        eventPublisher.publishEvent(new CompanyStructureChangedEvent(companyId));
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Replace-all of the units a user covers beyond their own. Every unit must be this company's.
     * The user's own unit is dropped from the set -- it is already covered. Takes effect on the
     * user's next request (VisibilityService's cache is dropped once this commits).
     */
    @Transactional
    public UserResponse setExtraOrgUnits(UUID companyId, UUID userId, List<UUID> orgUnitIds) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        requirePlacementAuthority();
        User user = getCompanyUser(companyId, userId);
        Set<OrgUnit> units = new HashSet<>();
        for (UUID id : new HashSet<>(orgUnitIds)) {
            OrgUnit unit = orgUnitRepository.findById(id).orElseThrow(() -> new OrgUnitNotFoundException(id));
            if (!unit.getCompany().getId().equals(companyId)) {
                throw new OrgUnitNotFoundException(id);
            }
            if (user.getOrgUnit() == null || !user.getOrgUnit().getId().equals(id)) {
                units.add(unit);
            }
        }
        user.getExtraOrgUnits().clear();
        user.getExtraOrgUnits().addAll(units);
        eventPublisher.publishEvent(new CompanyStructureChangedEvent(companyId));
        return UserResponse.from(userRepository.save(user));
    }

    /**
     * Promotes a company user to that company's own "Company Admin" role -- distinct from
     * the platform Super Admin (Wrapzzo's own staff, company_id null). Every company gets a
     * Company Admin role auto-seeded at creation time (see CompanyService), so this only
     * ever reassigns an existing user onto it; it never creates the role itself.
     *
     * Takes effect on their very next request: permissions are resolved per user, not read from the token.
     */
    @Transactional
    public UserResponse makeCompanyAdmin(UUID companyId, UUID userId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        requirePlacementAuthority();
        User user = getCompanyUser(companyId, userId);
        Role companyAdminRole = roleRepository
                .findByCompanyIdAndName(companyId, COMPANY_ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Company Admin role missing for company " + companyId));

        // A Company Admin runs the whole company, so sees all of it (they keep their unit for what they place themselves).
        user.setRole(companyAdminRole);
        user.setCompanyWide(true);
        userRepository.save(user);
        eventPublisher.publishEvent(new CompanyStructureChangedEvent(companyId));
        accessControlCacheService.invalidateAfterCommit();
        return UserResponse.from(user);
    }

    /**
     * Moves a user to another role of the company. Company Admin is entered and left through
     * makeCompanyAdmin / removeCompanyAdmin, which also keep the company's last admin in place.
     */
    @Transactional
    public UserResponse changeRole(UUID companyId, UUID userId, UUID roleId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        requirePlacementAuthority();
        User user = getCompanyUser(companyId, userId);
        Role role = roleRepository.findById(roleId)
                .filter(candidate -> candidate.getCompany() != null && candidate.getCompany().getId().equals(companyId) && candidate.isActive())
                .orElseThrow(() -> new RoleNotFoundException(roleId));
        if (COMPANY_ADMIN_ROLE_NAME.equals(role.getName()) || COMPANY_ADMIN_ROLE_NAME.equals(user.getRole().getName())) {
            throw new ConflictException("Use Make admin / Remove admin to move someone into or out of Company Admin");
        }
        user.setRole(role);
        userRepository.save(user);
        eventPublisher.publishEvent(new CompanyStructureChangedEvent(companyId));
        accessControlCacheService.invalidateAfterCommit();
        return UserResponse.from(user);
    }

    /**
     * Demotes a Company Admin to a role of the company's choosing. Refuses to leave a company with
     * zero admins; the demoted user's permissions follow their new role at once.
     */
    @Transactional
    public UserResponse removeCompanyAdmin(UUID companyId, UUID userId, UUID roleId) {
        companyAccessGuard.requireOwnCompanyOrPlatformStaff(companyId);
        requirePlacementAuthority();
        User user = getCompanyUser(companyId, userId);
        Role companyAdminRole = roleRepository
                .findByCompanyIdAndName(companyId, COMPANY_ADMIN_ROLE_NAME)
                .orElseThrow(() -> new IllegalStateException("Company Admin role missing for company " + companyId));
        if (userRepository.countByCompanyIdAndRoleId(companyId, companyAdminRole.getId()) <= 1) {
            throw new LastCompanyAdminException(companyId);
        }
        Role role = roleRepository.findById(roleId)
                .filter(candidate -> candidate.getCompany() != null && candidate.getCompany().getId().equals(companyId) && candidate.isActive())
                .filter(candidate -> !COMPANY_ADMIN_ROLE_NAME.equals(candidate.getName()))
                .orElseThrow(() -> new RoleNotFoundException(roleId));

        user.setRole(role);
        userRepository.save(user);
        accessControlCacheService.invalidateAfterCommit();
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
        User user = getVisibleCompanyUser(companyId, userId);
        try {
            userInviteTokenRepository.deleteUnusedByUserId(userId);
            userRepository.delete(user);
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new UserHasActivityException(userId);
        }
        eventPublisher.publishEvent(new CompanyStructureChangedEvent(companyId));
    }

    /** The value in the first org level that places a user at the company itself -- they see everything. */
    public static final String COMPANY_WIDE_PLACEMENT = "ALL";

    private static boolean isCompanyWidePlacement(List<String> orgUnitPath) {
        return orgUnitPath != null && !orgUnitPath.isEmpty() && COMPANY_WIDE_PLACEMENT.equalsIgnoreCase(orgUnitPath.get(0).trim());
    }

    /**
     * Where someone sits decides what they see, so changing a placement (company-wide, extra
     * units) needs someone who already sees the whole company -- a zonal manager holding
     * user.edit must not be able to widen their own, or anyone's, view beyond their own.
     */
    private void requirePlacementAuthority() {
        if (!hasPlacementAuthority()) {
            throw new AccessDeniedException("Only someone who sees the whole company can change who sees what");
        }
    }

    private boolean hasPlacementAuthority() {
        User caller = currentUser();
        return caller.getCompany() == null || caller.isCompanyWide();
    }

    private static boolean movesPlacement(User user, OrgUnit newUnit) {
        UUID current = user.getOrgUnit() != null ? user.getOrgUnit().getId() : null;
        UUID next = newUnit != null ? newUnit.getId() : null;
        return user.isCompanyWide() || !Objects.equals(current, next);
    }

    private User getCompanyUser(UUID companyId, UUID userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
        if (user.getCompany() == null || !user.getCompany().getId().equals(companyId)) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    /** A company user the caller can see (see Visibility) -- 404 otherwise, the same as one that doesn't exist. */
    private User getVisibleCompanyUser(UUID companyId, UUID userId) {
        User user = getCompanyUser(companyId, userId);
        if (!visibilityService.resolve(currentUser()).canSeeUser(user)) {
            throw new UserNotFoundException(userId);
        }
        return user;
    }

    private User currentUser() {
        UUID callerId = CurrentUser.id();
        return userRepository.findById(callerId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + callerId));
    }

    /**
     * All rows are checked before any write happens -- either the whole sheet imports
     * cleanly, or none of it does, so a bad row never leaves a half-imported roster.
     * existingByEmail is the one bulk-fetched lookup from bulkImport -- no query here.
     */
    private List<String> validate(UUID companyId, List<BulkImportUserRow> rows, Map<String, User> existingByEmail, Map<String, Role> rolesByName) {
        List<String> errors = new ArrayList<>();
        Set<String> emailsInBatch = new HashSet<>();

        for (int i = 0; i < rows.size(); i++) {
            BulkImportUserRow row = rows.get(i);
            String email = normalizedEmail(row.email());
            int rowNum = i + 1;

            Role role = rolesByName.get(row.role().trim().toLowerCase());
            if (role == null) {
                errors.add("Row " + rowNum + " (" + row.email() + "): role '" + row.role().trim() + "' is not an active role of this company -- add it in Roles first");
            } else if (COMPANY_ADMIN_ROLE_NAME.equals(role.getName())) {
                errors.add("Row " + rowNum + " (" + row.email() + "): Company Admin can't be set from a sheet -- import the user, then use Make admin");
            }

            if (!emailsInBatch.add(email)) {
                errors.add("Row " + rowNum + " (" + row.email() + "): duplicate email within this sheet");
            }
            User existing = existingByEmail.get(email);
            if (existing != null && !existing.getCompany().getId().equals(companyId)) {
                errors.add("Row " + rowNum + " (" + row.email() + "): this email already belongs to a different company");
            }
            if (isCompanyWidePlacement(row.orgUnitPath()) && row.orgUnitPath().stream().skip(1).anyMatch(level -> blankToNull(level) != null)) {
                errors.add("Row " + rowNum + " (" + row.email() + "): " + COMPANY_WIDE_PLACEMENT
                        + " in the first org level means the whole company -- leave the other levels empty");
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
