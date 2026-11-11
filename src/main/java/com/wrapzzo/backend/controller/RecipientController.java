package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.BulkDoctorsRequest;
import com.wrapzzo.backend.dto.ImportJobResponse;
import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.list.ListQueryParams;
import com.wrapzzo.backend.list.PageResponse;
import com.wrapzzo.backend.dto.UpcomingBirthdayResponse;
import com.wrapzzo.backend.dto.UpdateRecipientRequest;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.ImportJobService;
import com.wrapzzo.backend.service.RecipientService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    private final ImportJobService importJobs;

    /** One page: ?page=0&size=25&search=&sort=name&dir=asc&filters=[{field,operator,value}]. */
    @GetMapping("/page")
    @RequiresPermission(Keys.DOCTOR_VIEW)
    public PageResponse<RecipientResponse> page(ListQueryParams params) {
        return recipientService.page(params.toQuery());
    }

    /** One page of the doctors on the caller's own list -- for the Quick Gift form's picker. */
    @GetMapping("/mine/page")
    @PreAuthorize("hasAnyAuthority('" + Keys.DOCTOR_VIEW + "', '" + Keys.QUICK_GIFT_CREATE + "')")
    public PageResponse<RecipientResponse> minePage(ListQueryParams params) {
        return recipientService.pageMine(params.toQuery());
    }

    /** "Pick an existing doctor" for the Quick Gift form -- anyone who can send a gift can search, not just doctor.view holders. */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('" + Keys.DOCTOR_VIEW + "', '" + Keys.QUICK_GIFT_CREATE + "')")
    public List<RecipientResponse> search(@RequestParam String q) {
        return recipientService.search(q);
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
    public ResponseEntity<ImportJobResponse> bulkCreateMine(@Valid @RequestBody BulkDoctorsRequest request) {
        return importJobs.respond(importJobs.importDoctors(request.rows()));
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
