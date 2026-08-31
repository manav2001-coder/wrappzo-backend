package com.wrapzzo.backend.dto;

import com.wrapzzo.backend.model.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateOrderStatusRequest(@NotNull OrderStatus status) {
}
