package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.EmailSettingsRequest;
import com.wrapzzo.backend.dto.EmailSettingsResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.EmailSettingsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Platform config > Email settings: platform staff only (the keys are never grantable to a company). */
@RestController
@RequestMapping("/api/platform/email-settings")
@RequiredArgsConstructor
public class PlatformEmailSettingsController {

    private final EmailSettingsService emailSettings;

    @GetMapping
    @RequiresPermission(Keys.PLATFORM_EMAIL_SETTINGS_VIEW)
    public EmailSettingsResponse get() {
        return emailSettings.get();
    }

    @PutMapping
    @RequiresPermission(Keys.PLATFORM_EMAIL_SETTINGS_EDIT)
    public EmailSettingsResponse set(@Valid @RequestBody EmailSettingsRequest request) {
        return emailSettings.setWorkflowEmails(request.workflowEmailsEnabled());
    }
}
