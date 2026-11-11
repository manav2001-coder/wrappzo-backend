package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/** The real database after the migrations: a bypassed level can be written to the trail, and the permission exists for companies to hand out. */
@SpringBootTest
class ArtworkOverrideSchemaTest {

    @Autowired private JdbcTemplate jdbc;

    @Test
    void theApprovalTrailAcceptsABypassedLevel() {
        String rule = jdbc.queryForObject(
                "SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'chk_order_design_approvals_decision'", String.class);
        assertThat(rule).contains("APPROVED").contains("REJECTED").contains("BYPASSED");
    }

    @Test
    void theOverridePermissionIsRegisteredAndAssignableToCompanyRoles() {
        Map<String, Object> key = jdbc.queryForMap("SELECT module, action, company_assignable FROM access_controls WHERE key = 'artwork.override'");
        assertThat(key).containsEntry("module", "artwork").containsEntry("action", "override").containsEntry("company_assignable", true);
    }
}
