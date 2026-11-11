package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrderDesignApproval;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderDesignApprovalRepository extends JpaRepository<OrderDesignApproval, UUID> {

    /** Every decision on several proofs, oldest first -- one query for an order's versions or a queue. */
    List<OrderDesignApproval> findByDesign_IdInOrderByDecidedAtAsc(Collection<UUID> designIds);

    /** Has this person already decided on this proof (at any level)? One signature per person per proof. */
    boolean existsByDesign_IdAndDecidedBy_Id(UUID designId, UUID userId);

    /** Which of these proofs this person has already decided on -- one query for a whole queue instead of one per proof. */
    @Query("select distinct a.design.id from OrderDesignApproval a where a.design.id in :designIds and a.decidedBy.id = :userId")
    java.util.Set<UUID> findDesignIdsDecidedBy(@Param("designIds") Collection<UUID> designIds, @Param("userId") UUID userId);
}
