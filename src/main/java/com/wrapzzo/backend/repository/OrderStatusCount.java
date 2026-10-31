package com.wrapzzo.backend.repository;

import com.wrapzzo.backend.model.OrderStatus;

public interface OrderStatusCount {
    OrderStatus getStatus();

    long getCnt();
}
