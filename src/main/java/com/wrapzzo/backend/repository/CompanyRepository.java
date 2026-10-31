package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Company;
import com.wrapzzo.backend.model.CompanyStatus;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    long countByStatus(CompanyStatus status);
}
