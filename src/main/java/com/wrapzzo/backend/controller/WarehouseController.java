package com.wrapzzo.backend.controller;

import com.wrapzzo.backend.dto.UpdateOrderStatusRequest;
import com.wrapzzo.backend.dto.WarehouseOrderResponse;
import com.wrapzzo.backend.dto.WarehouseRequest;
import com.wrapzzo.backend.dto.WarehouseResponse;
import com.wrapzzo.backend.security.Permission.Keys;
import com.wrapzzo.backend.security.RequiresPermission;
import com.wrapzzo.backend.service.WarehouseService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The company's warehouses and the orders routed through them (WAREHOUSE_LOGISTICS) -- the caller's own company only. */
@RestController
@RequestMapping("/api/warehouses")
@RequiredArgsConstructor
public class WarehouseController {

    private final WarehouseService warehouseService;

    @GetMapping
    @RequiresPermission(Keys.WAREHOUSE_VIEW)
    public List<WarehouseResponse> list() {
        return warehouseService.list();
    }

    @PostMapping
    @RequiresPermission(Keys.WAREHOUSE_SETUP)
    public WarehouseResponse create(@Valid @RequestBody WarehouseRequest request) {
        return warehouseService.create(request);
    }

    @PutMapping("/{id}")
    @RequiresPermission(Keys.WAREHOUSE_SETUP)
    public WarehouseResponse update(@PathVariable UUID id, @Valid @RequestBody WarehouseRequest request) {
        return warehouseService.update(id, request);
    }

    @GetMapping("/orders")
    @RequiresPermission(Keys.WAREHOUSE_VIEW)
    public List<WarehouseOrderResponse> orders() {
        return warehouseService.listOrders();
    }

    @PostMapping("/orders/{orderId}/status")
    @RequiresPermission(Keys.WAREHOUSE_OPERATE)
    public WarehouseOrderResponse advance(@PathVariable UUID orderId, @Valid @RequestBody UpdateOrderStatusRequest request) {
        return warehouseService.advance(orderId, request.status());
    }
}
