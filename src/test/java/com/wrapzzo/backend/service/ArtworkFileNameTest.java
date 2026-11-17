package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

/** A vendor's proof must be named PCode_DoctorName.pdf (the older name that also ends with the AM is still accepted): lenient about writing, strict about content. */
class ArtworkFileNameTest {

    private static boolean ok(String uploaded) {
        return ArtworkFileName.matches(uploaded, "PC-ASHA", "Dr. Asha Shah", "MR North", "AM-PATNA-SM");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PC-ASHA_Asha-Shah.pdf",
            "PC-ASHA_Asha_Shah.pdf",
            "pc-asha_asha shah.PDF",
            "PCASHA_AshaShah.pdf",
            "PC-ASHA_Dr-Asha-Shah.pdf",
            "PC-ASHA_Dr. Asha Shah.pdf",
            "PC-ASHA_Asha-Shah_v2.pdf",
            "PC-ASHA_Asha-Shah-v12.pdf",
            "PC-ÁSHA_Ásha-Shah.pdf",
            "  PC-ASHA_Asha-Shah.pdf  ",
            // the way proofs used to be named still works, so files already prepared are not refused
            "PC-ASHA_Asha-Shah_MR-North.pdf",
            "PC-ASHA_Asha-Shah_AM-PATNA-SM.pdf",
            "PC-ASHA_Asha-Shah_MR-North_v2.pdf",
    })
    void acceptsTheRightNameHoweverItIsWritten(String uploaded) {
        assertThat(ok(uploaded)).as(uploaded).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "proof.pdf",
            "final_v3.pdf",
            "PC-ASHA.pdf",
            "Asha-Shah.pdf",
            "Asha-Shah_PC-ASHA.pdf",
            "PC-OTHER_Asha-Shah.pdf",
            "PC-ASHA_Neha-Rao.pdf",
            "PC-ASHA_Asha-Shah_Someone-Else.pdf",
            "PC-ASHA_Asha-Shah_AM-DELHI-SM.pdf",
            "MR-North_Asha-Shah_PC-ASHA.pdf",
            "PC-ASHA_Asha-Shah_MR-North_extra.pdf",
            "",
    })
    @NullSource
    void refusesAnythingThatLeavesAPartOutOrGetsOneWrong(String uploaded) {
        assertThat(ok(uploaded)).as(String.valueOf(uploaded)).isFalse();
    }

    @Test
    void aNameThatEndsInVAndDigitsIsNotMistakenForAVersion() {
        assertThat(ArtworkFileName.matches("P1_Asha_Dev2.pdf", "P1", "Asha", "Dev2", null)).isTrue();
        assertThat(ArtworkFileName.matches("P1_Asha_Dev2_v3.pdf", "P1", "Asha", "Dev2", null)).isTrue();
    }

    @Test
    void anOrderWithNoDoctorNameNeedsOnlyThePcode() {
        assertThat(ArtworkFileName.matches("ORD-9.pdf", "ORD-9", null, "MR North", null)).isTrue();
        assertThat(ArtworkFileName.matches("ORD-9_MR-North.pdf", "ORD-9", null, "MR North", null)).isTrue();
        assertThat(ArtworkFileName.matches("ORD-8.pdf", "ORD-9", null, "MR North", null)).isFalse();
    }

    @Test
    void anAmWithNoSapCodeCanOnlyBeNamedByNameWhenTheNameCarriesOne() {
        assertThat(ArtworkFileName.matches("PC-ASHA_Asha-Shah_MR-North.pdf", "PC-ASHA", "Asha Shah", "MR North", null)).isTrue();
        assertThat(ArtworkFileName.matches("PC-ASHA_Asha-Shah_X1.pdf", "PC-ASHA", "Asha Shah", "MR North", "  ")).isFalse();
    }

    @Test
    void theStoredNameIsTidyAndCarriesTheVersion() {
        assertThat(ArtworkFileName.canonical("PC-ASHA", "Dr. Asha Shah", 3)).isEqualTo("PC-ASHA_Asha-Shah_v3.pdf");
        assertThat(ArtworkFileName.canonical("ORD-9", null, 1)).isEqualTo("ORD-9_v1.pdf");
        assertThat(ArtworkFileName.example("PC-ASHA", "Dr. Asha Shah")).isEqualTo("PC-ASHA_Asha-Shah");
    }
}
