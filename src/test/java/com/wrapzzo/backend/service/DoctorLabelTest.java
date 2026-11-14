package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DoctorLabelTest {

    @Test
    void theNameComesFirstThenThePcodeInBrackets() {
        assertThat(DoctorLabel.of("P-1001", "Dr. Asha Shah")).isEqualTo("Dr. Asha Shah (P-1001)");
        assertThat(DoctorLabel.of("  P-1001 ", " Dr. Asha Shah ")).isEqualTo("Dr. Asha Shah (P-1001)");
    }

    @Test
    void withoutAPcodeThereAreNoEmptyBrackets() {
        assertThat(DoctorLabel.of(null, "Dr. Asha Shah")).isEqualTo("Dr. Asha Shah");
        assertThat(DoctorLabel.of("  ", "Dr. Asha Shah")).isEqualTo("Dr. Asha Shah");
    }

    @Test
    void withoutANameJustThePcodeShows() {
        assertThat(DoctorLabel.of("P-1001", null)).isEqualTo("(P-1001)");
        assertThat(DoctorLabel.of(null, null)).isNull();
    }
}
