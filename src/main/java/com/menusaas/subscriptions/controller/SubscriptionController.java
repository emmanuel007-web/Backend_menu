package com.menusaas.subscriptions.controller;

import com.menusaas.shared.api.ApiResponse;
import com.menusaas.subscriptions.dto.PlanResponse;
import com.menusaas.subscriptions.dto.SubscriptionRequest;
import com.menusaas.subscriptions.dto.SubscriptionResponse;
import com.menusaas.subscriptions.service.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Subscriptions", description = "Planes y suscripción del restaurante (gestión manual en el MVP)")
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

    @Operation(summary = "Suscribirse a un plan (por código)")
    @PostMapping("/subscribe")
    public ApiResponse<SubscriptionResponse> subscribe(@Valid @RequestBody SubscriptionRequest request) {
        return ApiResponse.ok("Suscripción activada", subscriptionService.subscribe(request));
    }
}