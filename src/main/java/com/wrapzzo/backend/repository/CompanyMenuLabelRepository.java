package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.CompanyMenuLabel;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyMenuLabelRepository extends JpaRepository<CompanyMenuLabel, CompanyMenuLabel.Key> {

    List<CompanyMenuLabel> findById_CompanyId(UUID companyId);

    void deleteById_CompanyId(UUID companyId);
}
