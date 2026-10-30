package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrderDesignApproval;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderDesignApprovalRepository extends JpaRepository<OrderDesignApproval, UUID> {

    /** Every decision on several proofs, oldest first -- one query for an order's versions or a queue. */
    List<OrderDesignApproval> findByDesign_IdInOrderByDecidedAtAsc(Collection<UUID> designIds);

    /** Has this person already decided on this proof (at any level)? One signature per person per proof. */
    boolean existsByDesign_IdAndDecidedBy_Id(UUID designId, UUID userId);
}
