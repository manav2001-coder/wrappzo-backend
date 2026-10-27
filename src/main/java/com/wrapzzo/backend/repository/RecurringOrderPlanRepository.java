package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.RecurringOrderPlan;
import com.wrapzzo.backend.model.RecurringPlanStatus;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurringOrderPlanRepository extends JpaRepository<RecurringOrderPlan, UUID> {

    List<RecurringOrderPlan> findByRequestedByIdOrderByCreatedAtDesc(UUID userId);

    /** The plans due on or before {@code date} -- what RecurringOrderJob places. */
    List<RecurringOrderPlan> findByStatusAndNextRunOnLessThanEqual(RecurringPlanStatus status, LocalDate date);
}
