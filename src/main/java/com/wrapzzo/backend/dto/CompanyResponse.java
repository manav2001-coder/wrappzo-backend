package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        String displayId,
        String name,
        CompanyStatus status,
        String logoUrl,
        String contactPersonName,
        String contactEmail,
        String contactMobile,
        String gstin,
        String pan,
        String registrationNumber,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String pincode,
        String notes,
        Instant createdAt,
        Instant addedOn,
        String addedByName,
        Instant updatedOn,
        String updatedByName) {

    public static CompanyResponse from(Company company, String logoUrl) {
        return new CompanyResponse(
                company.getId(),
                "CID-" + company.getCompanyNo(),
                company.getName(),
                company.getStatus(),
                logoUrl,
                company.getContactPersonName(),
                company.getContactEmail(),
                company.getContactMobile(),
                company.getGstin(),
                company.getPan(),
                company.getRegistrationNumber(),
                company.getAddressLine1(),
                company.getAddressLine2(),
                company.getCity(),
                company.getState(),
                company.getPincode(),
                company.getNotes(),
                company.getCreatedAt(),
                company.getCreatedAt(),
                company.getCreatedByName(),
                company.getUpdatedAt(),
                company.getUpdatedByName());
    }
}
