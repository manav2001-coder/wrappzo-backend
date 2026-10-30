package com.wrapzzo.backend.controller;

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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Shared recipient directory -- Company Admin and Member alike can view and add. */
@RestController
@RequestMapping("/api/recipients")
@RequiredArgsConstructor
public class RecipientController {

    private final RecipientService recipientService;

    @GetMapping
    @RequiresPermission(Keys.RECIPIENT_VIEW)
    public List<RecipientResponse> list() {
        return recipientService.list();
    }

    /** "Pick an existing doctor" for the Quick Gift form -- anyone who can send a gift can search, not just recipient.manage holders. */
    @GetMapping("/search")
    @PreAuthorize("hasAnyAuthority('" + Keys.RECIPIENT_VIEW + "', '" + Keys.REQUEST_CREATE + "', '" + Keys.RECIPIENT_LIST_MANAGE + "')")
    public List<RecipientResponse> search(@RequestParam String q) {
        return recipientService.search(q);
    }

    /** An MR's own working list (recipient_assignments) -- not the full shared directory. */
    @GetMapping("/mine")
    @PreAuthorize("hasAnyAuthority('" + Keys.RECIPIENT_LIST_MANAGE + "', '" + Keys.REQUEST_CREATE + "')")
    public List<RecipientResponse> mine() {
        return recipientService.listMine();
    }

    @PostMapping
    @RequiresPermission(Keys.RECIPIENT_CREATE)
    public RecipientResponse create(@Valid @RequestBody CreateRecipientRequest request) {
        return recipientService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Keys.RECIPIENT_EDIT)
    public RecipientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRecipientRequest request) {
        return recipientService.update(id, request);
    }

    @GetMapping("/upcoming-birthdays")
    @RequiresPermission(Keys.RECIPIENT_VIEW)
    public List<UpcomingBirthdayResponse> upcomingBirthdays(@RequestParam(defaultValue = "30") int days) {
        return recipientService.upcomingBirthdays(days);
    }
}
