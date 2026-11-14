package com.wrapzzo.backend.service;

/** How a doctor is named wherever a person reads it: the name, then the pcode in brackets -- "Dr. Asha Shah (P-1001)". */
public final class DoctorLabel {

    private DoctorLabel() {
    }

    /** The pcode is left out (no empty brackets) when there is none; a missing name leaves just the pcode in brackets. */
    public static String of(String pcode, String name) {
        boolean hasPcode = pcode != null && !pcode.isBlank();
        boolean hasName = name != null && !name.isBlank();
        if (hasPcode && hasName) {
            return name.trim() + " (" + pcode.trim() + ")";
        }
        if (hasPcode) {
            return "(" + pcode.trim() + ")";
        }
        return hasName ? name.trim() : null;
    }
}
