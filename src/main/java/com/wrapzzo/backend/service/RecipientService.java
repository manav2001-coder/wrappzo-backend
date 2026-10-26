package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.dto.UpcomingBirthdayResponse;
import com.wrapzzo.backend.dto.UpdateRecipientRequest;
import com.wrapzzo.backend.exception.RecipientAlreadyExistsException;
import com.wrapzzo.backend.exception.RecipientNotFoundException;
import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.Recipient;
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
import java.util.UUID;
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
    private final CampaignService campaignService;

    @Transactional(readOnly = true)
    public List<RecipientResponse> list() {
        User user = currentUser();
        return recipientRepository.findByCompanyIdOrderByNameAsc(user.getCompany().getId()).stream()
                .map(RecipientResponse::from)
                .toList();
    }

    /** An MR's own working list (recipient_assignments) -- not the full shared directory. */
    @Transactional(readOnly = true)
    public List<RecipientResponse> listMine() {
        User user = currentUser();
        return recipientAssignmentRepository.findByMrUser_IdOrderByAssignedAtDesc(user.getId()).stream()
                .map(a -> RecipientResponse.from(a.getRecipient()))
                .toList();
    }

    private static final int SEARCH_LIMIT = 10;

    /** "Pick an existing doctor" for the Quick Gift form -- an MR doesn't need full recipient.manage just to find one. */
    @Transactional(readOnly = true)
    public List<RecipientResponse> search(String query) {
        User user = currentUser();
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return recipientRepository.search(user.getCompany().getId(), query.trim()).stream()
                .limit(SEARCH_LIMIT)
                .map(RecipientResponse::from)
                .toList();
    }

    @Transactional
    public RecipientResponse create(CreateRecipientRequest request) {
        User user = currentUser();
        Company company = user.getCompany();
        String email = blankToNull(request.email());
        recipientRepository.findMatch(company.getId(), normalizedName(request.name()), request.phone().trim(), normalizedEmail(email))
                .ifPresent(r -> {
                    throw new RecipientAlreadyExistsException(r.getName());
                });
        Recipient recipient = new Recipient(
                company, request.name().trim(), request.phone().trim(), email, blankToNull(request.address()), request.dateOfBirth(), user);
        return RecipientResponse.from(recipientRepository.save(recipient));
    }

    /** Trusted server-side lookup for "an existing doctor was picked" flows (e.g. Quick Gift) -- never trust client-typed fields for an id the client only chose, not typed. */
    @Transactional(readOnly = true)
    public RecipientResponse get(UUID id) {
        return RecipientResponse.from(getEntity(id));
    }

    @Transactional
    public RecipientResponse update(UUID id, UpdateRecipientRequest request) {
        Recipient recipient = getEntity(id);
        String email = blankToNull(request.email());
        recipientRepository
                .findMatchExcluding(recipient.getCompany().getId(), id, normalizedName(request.name()), request.phone().trim(), normalizedEmail(email))
                .ifPresent(r -> {
                    throw new RecipientAlreadyExistsException(r.getName());
                });
        recipient.setName(request.name().trim());
        recipient.setPhone(request.phone().trim());
        recipient.setEmail(email);
        recipient.setAddress(blankToNull(request.address()));
        recipient.setDateOfBirth(request.dateOfBirth());
        return RecipientResponse.from(recipient);
    }

    private Recipient getEntity(UUID id) {
        User user = currentUser();
        Recipient recipient = recipientRepository.findById(id).orElseThrow(() -> new RecipientNotFoundException(id));
        if (!recipient.getCompany().getId().equals(user.getCompany().getId())) {
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
        if (name == null || name.isBlank() || phone == null || phone.isBlank()) {
            return;
        }
        String cleanEmail = blankToNull(email);
        if (recipientRepository.findMatch(company.getId(), normalizedName(name), phone.trim(), normalizedEmail(cleanEmail)).isPresent()) {
            return;
        }
        recipientRepository.save(new Recipient(company, name.trim(), phone.trim(), cleanEmail, blankToNull(address), dateOfBirth, createdBy));
    }

    /** A Company Admin sees every upcoming birthday company-wide; a Member sees only the recipients they themselves added. */
    @Transactional(readOnly = true)
    public List<UpcomingBirthdayResponse> upcomingBirthdays(int daysAhead) {
        User user = currentUser();
        LocalDate today = LocalDate.now();
        List<Recipient> recipients = campaignService.canManage(user)
                ? recipientRepository.findByCompanyIdAndDateOfBirthIsNotNull(user.getCompany().getId())
                : recipientRepository.findByCompanyIdAndCreatedByIdAndDateOfBirthIsNotNull(user.getCompany().getId(), user.getId());
        return recipients.stream()
                .map(r -> Map.entry(r, daysUntilNextBirthday(r.getDateOfBirth(), today)))
                .filter(e -> e.getValue() <= daysAhead)
                .sorted(Comparator.comparingInt(e -> e.getValue()))
                .map(e -> UpcomingBirthdayResponse.from(e.getKey(), e.getValue()))
                .toList();
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
