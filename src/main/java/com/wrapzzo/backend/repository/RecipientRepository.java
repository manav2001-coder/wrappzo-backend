package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.Recipient;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecipientRepository extends JpaRepository<Recipient, UUID> {

    List<Recipient> findByCompanyIdOrderByNameAsc(UUID companyId);

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

    /** The doctor-directory dedupe: the same registration number in a company is the same doctor, whatever the name/phone say. */
    Optional<Recipient> findFirstByCompany_IdAndRegistrationNumberIgnoreCase(UUID companyId, String registrationNumber);

    // Visibility (see service.Visibility): a recipient is visible through whoever added it or
    // whoever has it on their list -- if any of them is visible to the caller, so is the recipient.

    @Query("""
            select r from Recipient r left join r.createdBy creator
            where r.company.id = :companyId
              and (:companyWide = true
                   or creator.id in :people or creator.orgUnit.id in :orgUnits
                   or exists (select 1 from RecipientAssignment a where a.recipient = r
                              and (a.mrUser.id in :people or a.mrUser.orgUnit.id in :orgUnits)))
            order by r.name asc
            """)
    List<Recipient> findVisible(@Param("companyId") UUID companyId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    @Query("""
            select r from Recipient r left join r.createdBy creator
            where r.company.id = :companyId
              and (lower(r.name) like lower(concat('%', :query, '%')) or r.phone like concat('%', :query, '%')
                   or lower(r.registrationNumber) like lower(concat('%', :query, '%')))
              and (:companyWide = true
                   or creator.id in :people or creator.orgUnit.id in :orgUnits
                   or exists (select 1 from RecipientAssignment a where a.recipient = r
                              and (a.mrUser.id in :people or a.mrUser.orgUnit.id in :orgUnits)))
            order by r.name asc
            """)
    List<Recipient> searchVisible(@Param("companyId") UUID companyId, @Param("query") String query, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    @Query("""
            select r from Recipient r left join r.createdBy creator
            where r.company.id = :companyId and r.dateOfBirth is not null
              and (:companyWide = true
                   or creator.id in :people or creator.orgUnit.id in :orgUnits
                   or exists (select 1 from RecipientAssignment a where a.recipient = r
                              and (a.mrUser.id in :people or a.mrUser.orgUnit.id in :orgUnits)))
            """)
    List<Recipient> findVisibleWithDateOfBirth(@Param("companyId") UUID companyId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);

    @Query("""
            select count(r) > 0 from Recipient r left join r.createdBy creator
            where r.id = :recipientId
              and (:companyWide = true
                   or creator.id in :people or creator.orgUnit.id in :orgUnits
                   or exists (select 1 from RecipientAssignment a where a.recipient = r
                              and (a.mrUser.id in :people or a.mrUser.orgUnit.id in :orgUnits)))
            """)
    boolean isVisible(@Param("recipientId") UUID recipientId, @Param("companyWide") boolean companyWide,
            @Param("people") Collection<UUID> people, @Param("orgUnits") Collection<UUID> orgUnits);
}
