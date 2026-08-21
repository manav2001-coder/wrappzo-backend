package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.AccessControl;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessControlRepository extends JpaRepository<AccessControl, UUID> {

    Optional<AccessControl> findByKey(String key);
}
