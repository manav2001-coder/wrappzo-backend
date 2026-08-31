package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.CreateRecipientRequest;
import com.wrapzzo.backend.dto.RecipientResponse;
import com.wrapzzo.backend.dto.UpcomingBirthdayResponse;
import com.wrapzzo.backend.dto.UpdateRecipientRequest;
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
    @PreAuthorize("hasAuthority('recipient.manage')")
    public List<RecipientResponse> list() {
        return recipientService.list();
    }

    @PostMapping
    @PreAuthorize("hasAuthority('recipient.manage')")
    public RecipientResponse create(@Valid @RequestBody CreateRecipientRequest request) {
        return recipientService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('recipient.manage')")
    public RecipientResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRecipientRequest request) {
        return recipientService.update(id, request);
    }

    @GetMapping("/upcoming-birthdays")
    @PreAuthorize("hasAuthority('recipient.manage')")
    public List<UpcomingBirthdayResponse> upcomingBirthdays(@RequestParam(defaultValue = "30") int days) {
        return recipientService.upcomingBirthdays(days);
    }
}
