package com.wrapzzo.backend.service;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The name a vendor must give an artwork proof: {@code PCode_DoctorName_AMName.pdf}, where the AM's SAP code (employee code) may stand
 * in for their name. The match is lenient about how the name is written -- capitals, accents, and spaces, hyphens or underscores
 * (or none) between the parts do not matter, a doctor's "Dr." is optional, and a trailing "_v2" is ignored -- but every part must be there and right.
 * The proof is then stored under {@link #canonical}, so every proof carries the same tidy name whatever the vendor typed.
 */
public final class ArtworkFileName {

    private static final String TITLE = "(?i)^(dr|prof|mr|mrs|ms)\\b\\.?\\s*";

    private ArtworkFileName() {
    }

    /** True when {@code uploadedName} names this order's proof: the pcode, the doctor and the AM (by name or code), in that order. */
    public static boolean matches(String uploadedName, String pcode, String doctorName, String amName, String amCode) {
        if (uploadedName == null) {
            return false;
        }
        String stem = uploadedName.trim().replaceFirst("(?i)\\.pdf$", "").replaceFirst("(?i)[\\s_\\-]+v\\d+$", "");
        String actual = compact(stem);
        for (String expected : expectedNames(pcode, doctorName, amName, amCode)) {
            if (actual.equals(expected)) {
                return true;
            }
        }
        return false;
    }

    /** PCODE_Doctor_AM_vN.pdf -- the name the proof is stored and downloaded under. The doctor's title is dropped; a missing doctor is left out. */
    public static String canonical(String pcode, String doctorName, String amName, int version) {
        return "%s_v%d.pdf".formatted(example(pcode, doctorName, amName), version);
    }

    /** What to show a vendor as the right way to name it: PCODE_Doctor_AM.pdf. */
    public static String example(String pcode, String doctorName, String amName) {
        List<String> parts = new ArrayList<>();
        parts.add(FileNaming.part(pcode));
        String doctor = withoutTitle(doctorName);
        if (!doctor.isBlank()) {
            parts.add(FileNaming.part(doctor));
        }
        parts.add(FileNaming.part(amName));
        return String.join("_", parts);
    }

    private static List<String> expectedNames(String pcode, String doctorName, String amName, String amCode) {
        List<String> doctors = new ArrayList<>();
        String core = compact(withoutTitle(doctorName));
        if (core.isEmpty()) {
            doctors.add("");
        } else {
            doctors.add(core);
            doctors.add("dr" + core);
        }
        List<String> ams = new ArrayList<>();
        ams.add(compact(amName));
        if (amCode != null && !amCode.isBlank()) {
            ams.add(compact(amCode));
        }
        List<String> expected = new ArrayList<>();
        for (String doctor : doctors) {
            for (String am : ams) {
                expected.add(compact(pcode) + doctor + am);
            }
        }
        return expected;
    }

    private static String withoutTitle(String name) {
        return name == null ? "" : name.trim().replaceFirst(TITLE, "");
    }

    /** Lower case, accents removed, only letters and digits left: "P-1001 Ásha Shah" and "p1001_asha-shah" are the same. */
    private static String compact(String text) {
        if (text == null) {
            return "";
        }
        return Normalizer.normalize(text, Normalizer.Form.NFKD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "");
    }
}
