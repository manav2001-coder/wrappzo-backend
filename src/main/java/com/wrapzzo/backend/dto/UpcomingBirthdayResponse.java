package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Recipient;
import java.time.LocalDate;
import java.util.UUID;

public record UpcomingBirthdayResponse(
        UUID id,
        String name,
        String phone,
        String email,
        String address,
        LocalDate dateOfBirth,
        int daysUntilBirthday) {

    public static UpcomingBirthdayResponse from(Recipient r, int daysUntilBirthday) {
        return new UpcomingBirthdayResponse(
                r.getId(), r.getName(), r.getPhone(), r.getEmail(), r.getAddress(), r.getDateOfBirth(), daysUntilBirthday);
    }
}
