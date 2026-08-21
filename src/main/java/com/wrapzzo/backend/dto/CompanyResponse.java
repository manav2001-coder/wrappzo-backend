package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(UUID id, String name, CompanyStatus status, String logoUrl, Instant createdAt) {

    public static CompanyResponse from(Company company, String logoUrl) {
        return new CompanyResponse(company.getId(), company.getName(), company.getStatus(), logoUrl, company.getCreatedAt());
    }
}
