package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.BulkDoctorsRequest;
import com.wrapzzo.backend.dto.BulkDoctorsResponse;
import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.dto.UpcomingBirthdayResponse;
import com.wrapzzo.backend.dto.UpdateRecipientRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.RecipientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Doctors: each user sees the ones they added or are mapped to and, up the reporting line, those of their org units. */
@RestController
@RequestMapping("/api/recipients")
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientService recipientService;

    @GetMapping
    @RequiresPermission(Keys.DOCTOR_VIEW)
    public List<RecipientResponse> list() {
        return recipientService.list();
    }

    /** "Pick an existing doctor" for the Quick Gift form -- anyone who can send a gift can search, not just doctor.view holders. */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('" + Keys.DOCTOR_VIEW + "', '" + Keys.QUICK_GIFT_CREATE + "')")
    public List<RecipientResponse> search(@RequestParam String q) {
        return recipientService.search(q);
    }

    /** The doctors on the caller's own list (recipient_assignments) -- who the Quick Gift form offers first. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('" + Keys.DOCTOR_VIEW + "', '" + Keys.QUICK_GIFT_CREATE + "')")
    public List<RecipientResponse> mine() {
        return recipientService.listMine();
    }

    /** Adds a doctor and puts them on the caller's list -- no approval step. */
    @PostMapping("/mine")
    @RequiresPermission(Keys.DOCTOR_CREATE)
    public RecipientResponse createMine(@Valid @RequestBody CreateRecipientRequest request) {
        return recipientService.createMine(request);
    }

    /** A sheet of doctors onto the caller's own list, all or none. */
    @PostMapping("/mine/bulk")
    @RequiresPermission(Keys.DOCTOR_IMPORT)
    public BulkDoctorsResponse bulkCreateMine(@Valid @RequestBody BulkDoctorsRequest request) {
        return recipientService.bulkCreateMine(request.rows());
    }

    /** Takes a doctor off the caller's list; they stay a company doctor. */
    @DeleteMapping("/mine/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequiresPermission(Keys.DOCTOR_REMOVE)
    public void removeMine(@PathVariable UUID id) {
        recipientService.removeMine(id);
    }

    /** Edits any doctor the caller can see: one they added, one they are mapped to, or one in their org units (see Visibility). */
    @PutMapping("/{id}")
    @RequiresPermission(Keys.DOCTOR_EDIT)
    public RecipientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRecipientRequest request) {
        return recipientService.update(id, request);
    }

    @GetMapping("/upcoming-birthdays")
    @RequiresPermission(Keys.DOCTOR_VIEW)
    public List<UpcomingBirthdayResponse> upcomingBirthdays(@RequestParam(defaultValue = "30") int days) {
        return recipientService.upcomingBirthdays(days);
    }
}
