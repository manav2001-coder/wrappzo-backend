package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Recipient;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    List<Recipient> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<Recipient> findByCompanyIdAndDateOfBirthIsNotNull(UUID companyId);

    /**
     * Dedupe check (name+phone+email, case/whitespace-insensitive) -- null email on both
     * sides counts as a match. name/phone/email must already be normalized (trimmed, name
     * and email lowercased) by the caller -- wrapping a possibly-null bind parameter in
     * trim()/lower() here makes Postgres unable to infer its type, and it falls back to
     * bytea, throwing "function pg_catalog.btrim(bytea) does not exist" for the (very
     * common) case of a recipient with no email.
     */
    @Query("""
            select r from Recipient r
            where r.company.id = :companyId
              and lower(trim(r.name)) = :name
              and trim(r.phone) = :phone
              and ((:email is null and r.email is null) or lower(trim(r.email)) = :email)
            """)
    Optional<Recipient> findMatch(@Param("companyId") UUID companyId, @Param("name") String name, @Param("phone") String phone,
            @Param("email") String email);

    /** Same as findMatch, but excludes one recipient -- used when editing so a recipient doesn't "match itself". */
    @Query("""
            select r from Recipient r
            where r.company.id = :companyId
              and r.id <> :excludeId
              and lower(trim(r.name)) = :name
              and trim(r.phone) = :phone
              and ((:email is null and r.email is null) or lower(trim(r.email)) = :email)
            """)
    Optional<Recipient> findMatchExcluding(@Param("companyId") UUID companyId, @Param("excludeId") UUID excludeId,
            @Param("name") String name, @Param("phone") String phone, @Param("email") String email);
}
