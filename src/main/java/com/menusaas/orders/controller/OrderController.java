package com.menusaas.orders.controller;

import com.menusaas.orders.dto.OrderResponse;
import com.menusaas.orders.dto.OrderStatusRequest;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.service.OrderService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Orders", description = "Gestión de pedidos del restaurante (tenant-scoped)")
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @Operation(summary = "Listar pedidos de mi restaurante (filtrable por estado)")
    @GetMapping
    public ApiResponse<List<OrderResponse>> list(@RequestParam(required = false) OrderStatus status) {
        return ApiResponse.ok(orderService.listMine(status));
    }

    @Operation(summary = "Obtener un pedido de mi restaurante por ID")
    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable Long id) {
        return ApiResponse.ok(orderService.getMine(id));
    }

    @Operation(summary = "Cambiar el estado de un pedido")
    @PatchMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(@PathVariable Long id, @Valid @RequestBody OrderStatusRequest request) {
        return ApiResponse.ok("Estado de pedido actualizado", orderService.updateStatusMine(id, request.status()));
    }
}
