package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Vendor;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VendorRepository extends JpaRepository<Vendor, UUID> {

    Optional<Vendor> findByContactEmail(String contactEmail);
}
