package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    List<User> findByCompanyId(UUID companyId);

    List<User> findByCompanyIdAndDesignation(UUID companyId, String designation);

    /** One round-trip for a whole bulk-import batch instead of a findByEmail per row. */
    List<User> findByEmailIn(Collection<String> emails);

    /** For bulk-import's employee-code uniqueness pre-check -- see UserService.validate. */
    List<User> findByCompanyIdAndEmployeeCodeIsNotNull(UUID companyId);

    /** Direct reports of a manager -- the building block for walking a whole reporting tree. */
    List<User> findByReportsToId(UUID managerId);

    boolean existsByReportsToId(UUID managerId);

    /** Everyone assigned to any of these org units -- paired with OrgUnitRepository.findSelfAndDescendants. */
    List<User> findByOrgUnitIdIn(Collection<UUID> orgUnitIds);

    /** Guards against removing the last Company Admin a company has left. */
    long countByCompanyIdAndRoleId(UUID companyId, UUID roleId);

    /** Populates the designation picker for a company's Access Control page -- see DesignationAccessService. */
    @Query("select distinct u.designation from User u where u.company.id = :companyId and u.designation is not null order by u.designation")
    List<String> findDistinctDesignationsByCompanyId(@Param("companyId") UUID companyId);
}
