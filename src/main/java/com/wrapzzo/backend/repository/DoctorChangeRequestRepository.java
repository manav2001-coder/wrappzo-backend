package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.DoctorChangeRequest;
import com.wrapzzo.backend.model.DoctorChangeStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DoctorChangeRequestRepository extends JpaRepository<DoctorChangeRequest, UUID> {

    List<DoctorChangeRequest> findByRequestedBy_IdOrderByCreatedAtDesc(UUID requestedById);

    /** Pending requests from anyone who reports directly to this manager -- the manager's own approval queue. */
    List<DoctorChangeRequest> findByRequestedBy_ReportsTo_IdAndStatusOrderByCreatedAtAsc(UUID managerId, DoctorChangeStatus status);

    /** Company-wide pending queue -- the recipient.manage escalation path for when a direct manager isn't available. */
    List<DoctorChangeRequest> findByCompany_IdAndStatusOrderByCreatedAtAsc(UUID companyId, DoctorChangeStatus status);
}
