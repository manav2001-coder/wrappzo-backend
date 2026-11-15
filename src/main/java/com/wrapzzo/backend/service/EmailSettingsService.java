package com.wrapzzo.backend.service;

import com.wrapzzo.backend.dto.EmailSettingsResponse;
import com.wrapzzo.backend.model.PlatformSetting;
import com.wrapzzo.backend.repository.UserRepository;
import com.wrapzzo.backend.security.CurrentUser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** Platform config > Email settings: switch the order-workflow emails on or off for the whole platform. */
@Service
@RequiredArgsConstructor
public class EmailSettingsService {

    private final PlatformSettings settings;
    private final UserRepository users;

    public EmailSettingsResponse get() {
        return settings.find(PlatformSettings.WORKFLOW_EMAILS_ENABLED).map(EmailSettingsService::toResponse)
                .orElseGet(() -> new EmailSettingsResponse(true, null, null));
    }

    public EmailSettingsResponse setWorkflowEmails(boolean enabled) {
        String changedBy = users.findById(CurrentUser.id()).map(user -> user.getName()).orElse("Unknown");
        return toResponse(settings.save(PlatformSettings.WORKFLOW_EMAILS_ENABLED, Boolean.toString(enabled), changedBy));
    }

    private static EmailSettingsResponse toResponse(PlatformSetting setting) {
        return new EmailSettingsResponse(Boolean.parseBoolean(setting.getValue()), setting.getUpdatedAt(), setting.getUpdatedByName());
    }
}
