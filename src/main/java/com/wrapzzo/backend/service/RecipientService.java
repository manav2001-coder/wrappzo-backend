package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.BulkDoctorRow;
import com.wrapzzo.backend.dto.BulkDoctorsResponse;
import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.DoctorDetails;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.list.ListQuery;
import com.wrapzzo.backend.list.ListSpecs;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.dto.UpcomingBirthdayResponse;
import com.wrapzzo.backend.dto.UpdateRecipientRequest;
import com.wrapzzo.backend.exception.RecipientAlreadyExistsException;
import com.wrapzzo.backend.exception.RecipientNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Recipient;
import com.wrapzzo.backend.model.RecipientAssignment;
import com.wrapzzo.backend.model.User;
import com.wrapzzo.backend.repository.RecipientAssignmentRepository;
import com.wrapzzo.backend.repository.RecipientRepository;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A company-wide recipient directory -- just a rolling address book, not linked back to the
 * Quick Gift requests / Submissions that fed it. Deduped by name+phone+email (see
 * RecipientRepository.findMatch): a manual add rejects an exact match, while the
 * best-effort upsert used by Quick Gift/Campaign submissions silently skips one instead.
 */
@Service
@RequiredArgsConstructor
public class RecipientService {

    private final RecipientRepository recipientRepository;
    private final RecipientAssignmentRepository recipientAssignmentRepository;
    private final UserRepository userRepository;
    private final VisibilityService visibilityService;

    /**
     * One page of the doctors the caller can reach, searched, filtered and sorted in the database. Someone with nobody below them
     * (an AM or MR) gets their own list; managers everything in reach (their team and org units).
     */
    @Transactional(readOnly = true)
    public PageResponse<RecipientResponse> page(ListQuery query) {
        User user = currentUser();
        Visibility visibility = visibilityService.resolve(user);
        var scope = visibility.ownRecordsOnly()
                ? RecipientListFields.onListOf(user.getId())
                : RecipientListFields.visibleIn(user.getCompany().getId(), visibility);
        return pageOf(scope, query, user);
    }

    /** One page of just the doctors on the caller's own list -- who the Quick Gift form offers first. */
    @Transactional(readOnly = true)
    public PageResponse<RecipientResponse> pageMine(ListQuery query) {
        User user = currentUser();
        return pageOf(RecipientListFields.onListOf(user.getId()), query, user);
    }

    private PageResponse<RecipientResponse> pageOf(org.springframework.data.jpa.domain.Specification<Recipient> scope, ListQuery query, User user) {
        return ListSpecs.page(recipientRepository, scope, query, RecipientListFields.fields(), org.springframework.data.domain.Sort.by("name"), recipients -> {
            Set<UUID> onMyList = new java.util.HashSet<>(recipientAssignmentRepository.findRecipientIdsOnList(
                    user.getId(), recipients.stream().map(Recipient::getId).toList()));
            return recipients.stream().map(r -> RecipientResponse.from(r, onMyList.contains(r.getId()))).toList();
        });
    }

    private static final int SEARCH_LIMIT = 10;

    /** "Pick an existing doctor" for the Quick Gift form -- an MR doesn't need full recipient.manage just to find one. */
    @Transactional(readOnly = true)
    public List<RecipientResponse> search(String query) {
        User user = currentUser();
        if (query == null || query.isBlank()) {
            return List.of();
        }
        Visibility visibility = visibilityService.resolve(user);
        return recipientRepository
                .searchVisible(user.getCompany().getId(), query.trim(), visibility.companyWide(), visibility.peopleForQuery(),
                        visibility.orgUnitsForQuery(), org.springframework.data.domain.PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(RecipientResponse::from)
                .toList();
    }

    @Transactional
    public RecipientResponse create(CreateRecipientRequest request) {
        return RecipientResponse.from(createEntity(currentUser(), request));
    }

    private Recipient createEntity(User user, CreateRecipientRequest request) {
        Company company = user.getCompany();
        String email = blankToNull(request.email());
        DoctorDetails doctor = DoctorDetails.orEmpty(request.doctor());
        recipientRepository.findMatch(company.getId(), normalizedName(request.name()), request.phone().trim(), normalizedEmail(email))
                .ifPresent(r -> {
                    throw new RecipientAlreadyExistsException(r.getName());
                });
        String pcode = request.pcode().trim();
        rejectDuplicatePcode(company.getId(), pcode, null);
        Recipient recipient = new Recipient(
                company, request.name().trim(), request.phone().trim(), email, blankToNull(request.address()), request.dateOfBirth(), user);
        recipient.setPcode(pcode);
        applyDoctorDetails(recipient, doctor);
        return recipientRepository.save(recipient);
    }

    /**
     * An MR adds a doctor to their own list -- no approval. A doctor already in the directory under
     * the same pcode is the same doctor: they join this MR's list rather than becoming a duplicate
     * (which the unique index would also refuse).
     */
    @Transactional
    public RecipientResponse createMine(CreateRecipientRequest request) {
        User user = currentUser();
        UUID companyId = user.getCompany().getId();
        Recipient recipient = recipientRepository.findFirstByCompany_IdAndPcodeIgnoreCase(companyId, request.pcode().trim()).orElse(null);
        if (recipient == null) {
            recipient = createEntity(user, request);
        }
        if (!recipientAssignmentRepository.existsByRecipient_IdAndMrUser_Id(recipient.getId(), user.getId())) {
            recipientAssignmentRepository.save(new RecipientAssignment(recipient, user));
        }
        return RecipientResponse.from(recipient, true);
    }

    /**
     * Adds a whole sheet of doctors to the caller's list, all or none. A row that matches a doctor
     * already in the company (same pcode, or same name + phone + email) is that doctor: it joins the
     * list instead of becoming a duplicate.
     */
    @Transactional
    public BulkDoctorsResponse bulkCreateMine(List<BulkDoctorRow> rows) {
        User user = currentUser();
        UUID companyId = user.getCompany().getId();
        int created = 0;
        int joined = 0;
        int unchanged = 0;
        for (BulkDoctorRow row : rows) {
            String email = blankToNull(row.email());
            DoctorDetails doctor = DoctorDetails.orEmpty(row.doctor());
            String pcode = row.pcode().trim();
            Recipient recipient = recipientRepository.findFirstByCompany_IdAndPcodeIgnoreCase(companyId, pcode).orElse(null);
            if (recipient == null) {
                recipient = recipientRepository
                        .findMatch(companyId, normalizedName(row.name()), row.phone().trim(), normalizedEmail(email))
                        .orElse(null);
            }
            boolean isNew = recipient == null;
            if (isNew) {
                recipient = new Recipient(
                        user.getCompany(), row.name().trim(), row.phone().trim(), email, blankToNull(row.address()), row.dateOfBirth(), user);
                recipient.setPcode(pcode);
                applyDoctorDetails(recipient, doctor);
                recipient = recipientRepository.save(recipient);
            }
            if (recipientAssignmentRepository.existsByRecipient_IdAndMrUser_Id(recipient.getId(), user.getId())) {
                unchanged++;
                continue;
            }
            recipientAssignmentRepository.save(new RecipientAssignment(recipient, user));
            if (isNew) {
                created++;
            } else {
                joined++;
            }
        }
        return new BulkDoctorsResponse(created, joined, unchanged);
    }

    /** Takes a doctor off the caller's list; the doctor stays in the directory, so past orders keep pointing at them. */
    @Transactional
    public void removeMine(UUID id) {
        User user = currentUser();
        recipientAssignmentRepository.findByRecipient_IdAndMrUser_Id(id, user.getId()).ifPresent(recipientAssignmentRepository::delete);
    }

    /** Trusted server-side lookup for "an existing doctor was picked" flows (e.g. Quick Gift) -- never trust client-typed fields for an id the client only chose, not typed. */
    @Transactional(readOnly = true)
    public RecipientResponse get(UUID id) {
        return RecipientResponse.from(getEntity(id));
    }

    @Transactional
    public RecipientResponse update(UUID id, UpdateRecipientRequest request) {
        Recipient recipient = applyUpdate(getEntity(id), request);
        return RecipientResponse.from(recipient, recipientAssignmentRepository.existsByRecipient_IdAndMrUser_Id(id, currentUser().getId()));
    }

    private Recipient applyUpdate(Recipient recipient, UpdateRecipientRequest request) {
        UUID id = recipient.getId();
        String email = blankToNull(request.email());
        recipientRepository
                .findMatchExcluding(recipient.getCompany().getId(), id, normalizedName(request.name()), request.phone().trim(), normalizedEmail(email))
                .ifPresent(r -> {
                    throw new RecipientAlreadyExistsException(r.getName());
                });
        String pcode = request.pcode().trim();
        rejectDuplicatePcode(recipient.getCompany().getId(), pcode, id);
        recipient.setPcode(pcode);
        recipient.setName(request.name().trim());
        recipient.setPhone(request.phone().trim());
        recipient.setEmail(email);
        recipient.setAddress(blankToNull(request.address()));
        recipient.setDateOfBirth(request.dateOfBirth());
        // Omitted means "leave the doctor details alone" -- a form that doesn't show them must not wipe them.
        if (request.doctor() != null) {
            DoctorDetails doctor = request.doctor().normalized();
            applyDoctorDetails(recipient, doctor);
        }
        return recipient;
    }

    private void rejectDuplicatePcode(UUID companyId, String pcode, UUID excludeId) {
        recipientRepository.findFirstByCompany_IdAndPcodeIgnoreCase(companyId, pcode)
                .filter(r -> !r.getId().equals(excludeId))
                .ifPresent(r -> {
                    throw RecipientAlreadyExistsException.pcode(pcode, r.getName());
                });
    }

    private static void applyDoctorDetails(Recipient recipient, DoctorDetails doctor) {
        recipient.setQualification(doctor.qualification());
        recipient.setSpeciality(doctor.speciality());
        recipient.setClinicName(doctor.clinicName());
    }

    /** 404 for another company's recipient and for one the caller can't see (see Visibility) alike. */
    private Recipient getEntity(UUID id) {
        User user = currentUser();
        Recipient recipient = recipientRepository.findById(id).orElseThrow(() -> new RecipientNotFoundException(id));
        if (!recipient.getCompany().getId().equals(user.getCompany().getId())) {
            throw new RecipientNotFoundException(id);
        }
        Visibility visibility = visibilityService.resolve(user);
        if (!visibility.companyWide()
                && !recipientRepository.isVisible(id, false, visibility.peopleForQuery(), visibility.orgUnitsForQuery())) {
            throw new RecipientNotFoundException(id);
        }
        return recipient;
    }

    /**
     * Called from Quick Gift and Campaign submission creation -- best-effort, never blocks
     * the caller: silently no-ops if there's already a matching recipient, or if name/phone
     * are missing (a campaign creator may have removed those fields from their form).
     */
    @Transactional
    public void upsertFromSubmission(Company company, String name, String phone, String email, String address, LocalDate dateOfBirth,
            User createdBy) {
        upsertFromSubmission(company, name, phone, email, address, dateOfBirth, null, DoctorDetails.EMPTY, createdBy);
    }

    /**
     * Same, carrying a doctor's pcode and print details -- a known pcode counts as "already there" too.
     * A doctor needs a pcode, so without one nothing is added to the directory (the caller's own record is unaffected).
     */
    @Transactional
    public void upsertFromSubmission(Company company, String name, String phone, String email, String address, LocalDate dateOfBirth,
            String pcode, DoctorDetails doctor, User createdBy) {
        if (name == null || name.isBlank() || phone == null || phone.isBlank() || pcode == null || pcode.isBlank()) {
            return;
        }
        if (recipientRepository.findFirstByCompany_IdAndPcodeIgnoreCase(company.getId(), pcode.trim()).isPresent()) {
            return;
        }
        String cleanEmail = blankToNull(email);
        if (recipientRepository.findMatch(company.getId(), normalizedName(name), phone.trim(), normalizedEmail(cleanEmail)).isPresent()) {
            return;
        }
        Recipient recipient = new Recipient(company, name.trim(), phone.trim(), cleanEmail, blankToNull(address), dateOfBirth, createdBy);
        recipient.setPcode(pcode.trim());
        applyDoctorDetails(recipient, doctor);
        recipientRepository.save(recipient);
        // A doctor you add -- here, through a Quick Gift or a campaign entry -- is on your list, like one added on the Doctors screen.
        if (createdBy != null) {
            recipientAssignmentRepository.save(new RecipientAssignment(recipient, createdBy));
        }
    }

    /** Upcoming birthdays of the recipients the caller can see (see Visibility). */
    @Transactional(readOnly = true)
    public List<UpcomingBirthdayResponse> upcomingBirthdays(int daysAhead) {
        User user = currentUser();
        LocalDate today = LocalDate.now();
        Visibility visibility = visibilityService.resolve(user);
        // Only the doctors whose birthday falls in the window are read from the database -- not every doctor in reach.
        Set<Integer> codes = birthdayCodes(today, daysAhead);
        List<Recipient> recipients = visibility.ownRecordsOnly()
                ? recipientRepository.findOnListWithBirthdayIn(user.getId(), codes)
                : recipientRepository.findVisibleWithBirthdayIn(user.getCompany().getId(), visibility.companyWide(), visibility.peopleForQuery(),
                        visibility.orgUnitsForQuery(), codes);
        return recipients.stream()
                .map(r -> Map.entry(r, daysUntilNextBirthday(r.getDateOfBirth(), today)))
                .filter(e -> e.getValue() <= daysAhead)
                .sorted(Comparator.comparingInt(e -> e.getValue()))
                .map(e -> UpcomingBirthdayResponse.from(e.getKey(), e.getValue()))
                .toList();
    }

    /** Every month*100+day that falls from today through {@code daysAhead} days on -- a Feb 29 birthday counts on Feb 28 in a year without one. */
    static Set<Integer> birthdayCodes(LocalDate today, int daysAhead) {
        Set<Integer> codes = new java.util.HashSet<>();
        for (int i = 0; i <= Math.max(daysAhead, 0); i++) {
            LocalDate day = today.plusDays(i);
            codes.add(day.getMonthValue() * 100 + day.getDayOfMonth());
            if (day.getMonthValue() == 2 && day.getDayOfMonth() == 28 && !day.isLeapYear()) {
                codes.add(229);
            }
        }
        return codes;
    }

    /** Handles Feb 29 birthdays in a non-leap year via MonthDay.atYear's built-in clamp to Feb 28. */
    private int daysUntilNextBirthday(LocalDate dateOfBirth, LocalDate today) {
        MonthDay monthDay = MonthDay.from(dateOfBirth);
        LocalDate nextBirthday = monthDay.atYear(today.getYear());
        if (nextBirthday.isBefore(today)) {
            nextBirthday = monthDay.atYear(today.getYear() + 1);
        }
        return (int) ChronoUnit.DAYS.between(today, nextBirthday);
    }

    private String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    /** Matches the query's own `lower(trim(r.name))` -- normalize here so the bind parameter isn't wrapped in SQL trim()/lower(). */
    private String normalizedName(String name) {
        return name.trim().toLowerCase();
    }

    private String normalizedEmail(String email) {
        return email == null ? null : email.toLowerCase();
    }

    private User currentUser() {
        UUID userId = CurrentUser.id();
        return userRepository.findById(userId).orElseThrow(() -> new IllegalStateException("Authenticated user not found: " + userId));
    }
}
