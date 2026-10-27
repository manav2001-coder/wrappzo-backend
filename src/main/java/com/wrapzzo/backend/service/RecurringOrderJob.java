package com.wrapzzo.backend.service;

import com.wrapzzo.backend.service.RecurringOrderService.DuePlan;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * Places each recurring order on its day. Every plan runs in its own transaction, as its own
 * requester, so one failure (the gift left the requester's roster, say) is recorded on that
 * plan and never stops the rest.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringOrderJob {

    private final RecurringOrderService recurringOrderService;

    @Scheduled(cron = "0 15 6 * * *")
    public void placeDueOrders() {
        List<DuePlan> due = recurringOrderService.findDue(LocalDate.now());
        int placed = 0;
        for (DuePlan plan : due) {
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(plan.requesterId(), null, List.of()));
            try {
                recurringOrderService.placeDue(plan.planId());
                placed++;
            } catch (RuntimeException e) {
                log.warn("Recurring order plan {} failed: {}", plan.planId(), e.getMessage());
                recurringOrderService.recordFailure(plan.planId(), e.getMessage());
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
        log.info("Recurring orders: {} of {} due plans placed", placed, due.size());
    }
}
