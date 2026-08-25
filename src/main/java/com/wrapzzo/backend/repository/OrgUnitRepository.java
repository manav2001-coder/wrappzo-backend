package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrgUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrgUnitRepository extends JpaRepository<OrgUnit, UUID> {

    List<OrgUnit> findByCompanyId(UUID companyId);

    /** Every org unit at or under orgUnitId -- uses the ancestor_ids GIN index (see V2). */
    @Query(value = "SELECT * FROM org_units WHERE id = :orgUnitId OR ancestor_ids @> ARRAY[:orgUnitId]::uuid[]", nativeQuery = true)
    List<OrgUnit> findSelfAndDescendants(@Param("orgUnitId") UUID orgUnitId);
}
