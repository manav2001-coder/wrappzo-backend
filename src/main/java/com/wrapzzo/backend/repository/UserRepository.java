package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByCompanyId(UUID companyId);

    /** One round-trip for a whole bulk-import batch instead of a findByEmail per row. */
    List<User> findByEmailIn(Collection<String> emails);

    /** For bulk-import's employee-code uniqueness pre-check -- see UserService.validate. */
    List<User> findByCompanyIdAndEmployeeCodeIsNotNull(UUID companyId);
}
