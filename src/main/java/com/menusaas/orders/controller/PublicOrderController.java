package com.menusaas.orders.controller;

import com.menusaas.orders.dto.CreateOrderRequest;
import com.menusaas.orders.dto.OrderResponse;
import com.menusaas.orders.service.OrderService;
import com.menusaas.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Public Orders", description = "Recepción de pedidos desde el menú público")
@RestController
@RequestMapping("/api/public/orders")
@RequiredArgsConstructor
public class PublicOrderController {

    private final OrderService orderService;

    @Operation(summary = "Crear un nuevo pedido desde el menú digital (sin autenticación)")
    @PostMapping("/{slug}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderResponse> createOrder(@PathVariable String slug,
                                                  @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok("Pedido recibido exitosamente", orderService.createPublicOrder(slug, request));
    }
}
