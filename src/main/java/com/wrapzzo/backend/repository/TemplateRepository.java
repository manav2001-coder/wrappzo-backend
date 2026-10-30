package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Template;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TemplateRepository extends JpaRepository<Template, UUID> {

    /** A company's own templates plus every system master template. */
    @Query("select t from Template t where t.company.id = :companyId or t.company is null order by t.name")
    List<Template> findAvailableToCompany(@Param("companyId") UUID companyId);

    @Query("select t from Template t where t.company is null order by t.name")
    List<Template> findSystem();

    Optional<Template> findByCompanyIdAndName(UUID companyId, String name);

    @Query("select t from Template t where t.company is null and t.name = :name")
    Optional<Template> findSystemByName(@Param("name") String name);

    /** Every inclusion edge as (template id, included template id) -- a small table, read whole to resolve. */
    @Query(value = "select template_id, referenced_template_id from template_references", nativeQuery = true)
    List<Object[]> findAllReferenceEdges();

    /** Direct keys of the given ACTIVE templates as (template id, key). */
    @Query(value = """
            select tac.template_id, ac.key
            from template_access_controls tac
            join access_controls ac on ac.id = tac.access_control_id
            join templates t on t.id = tac.template_id
            where t.is_active and tac.template_id in (:ids)
            """, nativeQuery = true)
    List<Object[]> findDirectKeys(@Param("ids") Collection<UUID> ids);

    @Query(value = "select template_id from role_templates where role_id = :roleId", nativeQuery = true)
    List<UUID> findTemplateIdsByRoleId(@Param("roleId") UUID roleId);

    @Query(value = "select template_id from user_templates where user_id = :userId", nativeQuery = true)
    List<UUID> findTemplateIdsByUserId(@Param("userId") UUID userId);

    @Query(value = "select count(*) from role_templates where template_id = :id", nativeQuery = true)
    long countRoleUses(@Param("id") UUID id);

    @Query(value = "select count(*) from user_templates where template_id = :id", nativeQuery = true)
    long countUserUses(@Param("id") UUID id);

    @Query(value = "select count(*) from template_references where referenced_template_id = :id", nativeQuery = true)
    long countReferencedBy(@Param("id") UUID id);
}
