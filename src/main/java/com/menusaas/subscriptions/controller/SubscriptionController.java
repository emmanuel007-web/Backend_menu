package com.menusaas.subscriptions.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.subscriptions.dto.PlanResponse;
import com.menusaas.subscriptions.dto.SubscribeResult;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Subscriptions", description = "Planes y suscripción del restaurante (ciclo de vida completo)")
@RestController
@RequestMapping("/api/subscriptions")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionService subscriptionService;

    @Operation(summary = "Listar planes disponibles")
    @GetMapping("/plans")
    public ApiResponse<List<PlanResponse>> listPlans() {
        return ApiResponse.ok(subscriptionService.listPlans());
    }

    @Operation(summary = "Mi suscripción actual")
    @GetMapping("/me")
    public ApiResponse<SubscriptionResponse> getMine() {
        return ApiResponse.ok(subscriptionService.getMySubscription());
    }

    @Operation(summary = "Suscribirse/cambiar a un plan (redirige a la pasarela si está configurada)")
    @PostMapping("/subscribe")
    public ApiResponse<SubscribeResult> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        return ApiResponse.ok("Suscripción procesada", subscriptionService.subscribe(request));
    }

    @Operation(summary = "Cancelar la suscripción activa")
    @PostMapping("/cancel")
    public ApiResponse<SubscriptionResponse> cancel() {
        return ApiResponse.ok("Suscripción cancelada", subscriptionService.cancelMySubscription());
    }
}