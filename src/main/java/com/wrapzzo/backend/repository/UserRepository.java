package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    /** Sign-in: an address matches whatever capitals it was stored or typed with. */
    Optional<User> findByEmailIgnoreCase(String email);

    List<User> findByCompanyId(UUID companyId);

    /** Everyone holding a role in a company -- an approval step resolves its approvers from this. */
    List<User> findByCompanyIdAndRoleId(UUID companyId, UUID roleId);


    /** One round-trip for a whole bulk-import batch instead of a findByEmail per row. */
    List<User> findByEmailIn(Collection<String> emails);

    /** For bulk-import's employee-code uniqueness pre-check -- see UserService.validate. */
    List<User> findByCompanyIdAndEmployeeCodeIsNotNull(UUID companyId);

    boolean existsByReportsToId(UUID managerId);

    /** Everyone assigned to any of these org units -- paired with OrgUnitRepository.findSelfAndDescendants. */
    List<User> findByOrgUnitIdIn(Collection<UUID> orgUnitIds);

    /** Guards against removing the last Company Admin a company has left. */
    long countByCompanyIdAndRoleId(UUID companyId, UUID roleId);

    /**
     * Everyone below the manager in the reporting line, at any depth (not the manager). UNION,
     * not UNION ALL, so a cycle in reports_to_id can't recurse forever.
     */
    @Query(value = """
            WITH RECURSIVE reporting_line AS (
                SELECT id FROM users WHERE reports_to_id = :managerId
                UNION
                SELECT u.id FROM users u JOIN reporting_line r ON u.reports_to_id = r.id
            )
            SELECT id FROM reporting_line
            """, nativeQuery = true)
    List<UUID> findReportingLineIds(@Param("managerId") UUID managerId);
}
