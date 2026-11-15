package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

/** Real database: the switch is stored, read back, defaults to on, and the server that changed it follows at once. */
@SpringBootTest
@Transactional
class PlatformSettingsIntegrationTest {

    @Autowired private PlatformSettings settings;

    @AfterEach
    void leaveItOn() {
        settings.save(PlatformSettings.WORKFLOW_EMAILS_ENABLED, "true", "test");
        settings.forgetReads();
    }

    @Test
    void workflowEmailsAreOnUntilSomeoneSwitchesThemOff() {
        settings.forgetReads();
        assertThat(settings.workflowEmailsEnabled()).isTrue();
    }

    @Test
    void aChangeIsSavedWithWhoMadeItAndTheServerFollowsAtOnce() {
        settings.save(PlatformSettings.WORKFLOW_EMAILS_ENABLED, "false", "Priya (Super Admin)");

        assertThat(settings.workflowEmailsEnabled()).isFalse();
        var saved = settings.find(PlatformSettings.WORKFLOW_EMAILS_ENABLED).orElseThrow();
        assertThat(saved.getValue()).isEqualTo("false");
        assertThat(saved.getUpdatedByName()).isEqualTo("Priya (Super Admin)");
        assertThat(saved.getUpdatedAt()).isNotNull();

        settings.save(PlatformSettings.WORKFLOW_EMAILS_ENABLED, "true", "Priya (Super Admin)");
        assertThat(settings.workflowEmailsEnabled()).isTrue();
    }
}
