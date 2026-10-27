package com.wrapzzo.backend.exception;

import java.util.UUID;

public class RecurringOrderPlanNotFoundException extends RuntimeException {

    public RecurringOrderPlanNotFoundException(UUID id) {
        super("Recurring order plan not found: " + id);
    }
}
