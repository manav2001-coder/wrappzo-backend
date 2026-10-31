package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Vendor;
import com.wrapzzo.backend.model.VendorStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByContactEmail(String contactEmail);

    List<Vendor> findByStatus(VendorStatus status);

    long countByStatus(VendorStatus status);
}
