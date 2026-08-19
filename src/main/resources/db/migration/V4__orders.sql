-- V4: Estructura de tablas para el módulo de pedidos (orders y order_items)

CREATE TABLE orders (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    order_number  VARCHAR(30)   NOT NULL,
    customer_name VARCHAR(120)  NOT NULL,
    customer_phone VARCHAR(30),
    table_number  VARCHAR(30),
    notes         TEXT,
    status        VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    total_amount  NUMERIC(12,2) NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_orders_restaurant_status ON orders (restaurant_id, status);
CREATE INDEX idx_orders_created ON orders (created_at);

CREATE TABLE order_items (
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT        NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id   BIGINT        REFERENCES products (id) ON DELETE SET NULL,
    product_name VARCHAR(160)  NOT NULL,
    unit_price   NUMERIC(12,2) NOT NULL,
    quantity     INT           NOT NULL DEFAULT 1,
    subtotal     NUMERIC(12,2) NOT NULL,
    notes        VARCHAR(255)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);
