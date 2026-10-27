package com.wrapzzo.backend.exception;

import java.util.UUID;

public class WarehouseNotFoundException extends RuntimeException {

    public WarehouseNotFoundException(UUID id) {
        super("Warehouse not found: " + id);
    }
}
