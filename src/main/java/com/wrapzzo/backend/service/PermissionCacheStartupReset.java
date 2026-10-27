package com.wrapzzo.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Clears the cached permissions once the app is up, after Flyway has run. A deploy that grants a
 * key to existing roles (e.g. menu.manage to Company Admin) then takes effect at once instead of
 * up to an hour later. Never blocks startup: if Redis is unreachable, the TTL still applies.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PermissionCacheStartupReset {

    private final AccessControlCacheService accessControlCacheService;

    @EventListener(ApplicationReadyEvent.class)
    public void clearOnStartup() {
        try {
            log.info("Cleared {} cached permission entries on startup", accessControlCacheService.clearAll());
        } catch (RuntimeException e) {
            log.warn("Couldn't clear the permission cache on startup: {}", e.getMessage());
        }
    }
}
