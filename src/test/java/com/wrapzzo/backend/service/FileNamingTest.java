package com.wrapzzo.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.wrapzzo.backend.model.User;
import org.junit.jupiter.api.Test;

class FileNamingTest {

    private User am(String code, String name) {
        User user = mock(User.class);
        when(user.getEmployeeCode()).thenReturn(code);
        when(user.getName()).thenReturn(name);
        return user;
    }

    @Test
    void aPhotoIsNamedAfterTheDoctorAndTheAm_likeAProof() {
        assertThat(FileNaming.photoName("MH-0258", am("AM-BERHAMPORE-SM", "Ravi"), "image/jpeg")).isEqualTo("MH-0258_AM-BERHAMPORE-SM_photo.jpg");
        assertThat(FileNaming.photoName("DUMMY-F13063DC", am("E 101", "Ravi"), "image/png; charset=x")).isEqualTo("DUMMY-F13063DC_E-101_photo.png");
    }

    @Test
    void theAmsNameStandsInWhenThereIsNoCode_andUnsafeCharactersBecomeHyphens() {
        assertThat(FileNaming.photoName("pc/1 2", am(null, "Asha D'Souza"), "image/webp")).isEqualTo("pc-1-2_Asha-D-Souza_photo.webp");
    }

    @Test
    void anUnknownPcodeOrTypeStillGivesASafeName() {
        assertThat(FileNaming.photoName(null, am("AM1", "x"), "application/octet-stream")).isEqualTo("NA_AM1_photo.jpg");
        assertThat(FileNaming.photoName("  ", am("AM1", "x"), null)).isEqualTo("NA_AM1_photo.jpg");
    }

    @Test
    void lastSegmentIsWhatTheFileDownloadsAs() {
        assertThat(FileNaming.lastSegment("quick-gifts/c/u/uuid/MH-1_AM1_photo.jpg")).isEqualTo("MH-1_AM1_photo.jpg");
        assertThat(FileNaming.lastSegment("legacy-key-without-slash")).isEqualTo("legacy-key-without-slash");
    }
}
