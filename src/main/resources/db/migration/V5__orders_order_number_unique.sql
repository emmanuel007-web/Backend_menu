-- V5: Red de seguridad contra duplicados de order_number por restaurante.
-- La generación del consecutivo ya está serializada con lock pesimista en
-- OrderService; este constraint garantiza que NUNCA existan duplicados.

ALTER TABLE orders
    ADD CONSTRAINT uk_orders_restaurant_order_number UNIQUE (restaurant_id, order_number);