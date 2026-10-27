package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Warehouse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, UUID> {

    List<Warehouse> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<Warehouse> findByCompanyIdAndActiveTrue(UUID companyId);
}
