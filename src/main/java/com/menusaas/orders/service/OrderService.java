package com.menusaas.orders.service;

import com.menusaas.orders.dto.*;
import com.menusaas.orders.entity.Order;
import com.menusaas.orders.entity.OrderItem;
import com.menusaas.orders.entity.OrderStatus;
import com.menusaas.orders.repository.OrderRepository;
import com.menusaas.products.entity.Product;
import com.menusaas.products.repository.ProductRepository;
import com.menusaas.restaurants.entity.Restaurant;
import com.menusaas.restaurants.repository.RestaurantRepository;
import com.menusaas.shared.api.BadRequestException;
import com.menusaas.shared.api.ResourceNotFoundException;
import com.menusaas.shared.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrderRepository orderRepository;
    private final RestaurantRepository restaurantRepository;
    private final ProductRepository productRepository;

    @Transactional
    public OrderResponse createPublicOrder(String slug, CreateOrderRequest request) {
        // Lock pesimista en la fila del restaurante: dos pedidos concurrentes del
        // mismo tenant se serializan aquí, evitando que count+1 genere duplicados.
        Restaurant restaurant = restaurantRepository.findBySlugForUpdate(slug.trim().toLowerCase())
                .filter(Restaurant::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("El menú digital no existe o no está disponible"));

        Long restaurantId = restaurant.getId();

        Order order = Order.builder()
                .restaurantId(restaurantId)
                .customerName(request.customerName().trim())
                .customerPhone(request.customerPhone() != null ? request.customerPhone().trim() : null)
                .tableNumber(request.tableNumber() != null ? request.tableNumber().trim() : null)
                .notes(request.notes() != null ? request.notes().trim() : null)
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;

        for (OrderItemRequest itemReq : request.items()) {
            Product product = productRepository.findByIdAndRestaurantId(itemReq.productId(), restaurantId)
                    .orElseThrow(() -> new BadRequestException("Producto no disponible en el menú: ID " + itemReq.productId()));

            if (!product.isAvailable()) {
                throw new BadRequestException("El producto '" + product.getName() + "' no se encuentra disponible actualmente");
            }

            BigDecimal unitPrice = product.getPrice();
            BigDecimal subtotal = unitPrice.multiply(BigDecimal.valueOf(itemReq.quantity()));
            total = total.add(subtotal);

            OrderItem item = OrderItem.builder()
                    .productId(product.getId())
                    .productName(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(itemReq.quantity())
                    .subtotal(subtotal)
                    .notes(itemReq.notes() != null ? itemReq.notes().trim() : null)
                    .build();

            order.addItem(item);
        }

        order.setTotalAmount(total);

        // Generación de consecutivo de pedido (ej. FMIX-0001)
        long count = orderRepository.countOrdersForRestaurant(restaurantId);
        String prefix = generatePrefix(restaurant.getSlug());
        order.setOrderNumber(String.format("%s-%04d", prefix, count + 1));

        Order saved = orderRepository.save(order);
        log.info("Nuevo pedido recibido: num={}, restaurante={}, cliente={}, total={}",
                saved.getOrderNumber(), restaurant.getSlug(), saved.getCustomerName(), saved.getTotalAmount());

        return OrderResponse.from(saved);
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listMine(OrderStatus status) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        List<Order> orders = (status != null)
                ? orderRepository.findByRestaurantIdAndStatusOrderByCreatedAtDesc(restaurantId, status)
                : orderRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);

        return orders.stream().map(OrderResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public OrderResponse getMine(Long id) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Order order = orderRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));
        return OrderResponse.from(order);
    }

    @Transactional
    public OrderResponse updateStatusMine(Long id, OrderStatus newStatus) {
        Long restaurantId = SecurityUtils.currentRestaurantId();
        Order order = orderRepository.findByIdAndRestaurantId(id, restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Pedido no encontrado"));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new BadRequestException("No se puede modificar un pedido cancelado");
        }
        if (order.getStatus() == OrderStatus.DELIVERED && newStatus != OrderStatus.DELIVERED) {
            throw new BadRequestException("No se puede modificar un pedido que ya fue entregado");
        }

        order.setStatus(newStatus);
        Order updated = orderRepository.save(order);
        log.info("Estado de pedido actualizado: id={}, num={}, nuevoEstado={}", updated.getId(), updated.getOrderNumber(), newStatus);
        return OrderResponse.from(updated);
    }

    private String generatePrefix(String slug) {
        String clean = slug.replaceAll("[^a-zA-Z0-9]", "").toUpperCase();
        if (clean.length() >= 4) {
            return clean.substring(0, 4);
        }
        return (clean + "ORD").substring(0, 4);
    }
}
