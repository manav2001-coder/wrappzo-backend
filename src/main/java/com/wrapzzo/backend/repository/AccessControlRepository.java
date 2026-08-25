package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.AccessControl;
import com.wrapzzo.backend.model.AccessControlType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccessControlRepository extends JpaRepository<AccessControl, UUID> {

    Optional<AccessControl> findByKey(String key);

    /** Super Admin's blanket-access set (see AccessControlCacheService) -- every FUNCTIONAL key, dynamically. */
    List<AccessControl> findByType(AccessControlType type);
}
