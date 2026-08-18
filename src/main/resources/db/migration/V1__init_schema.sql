-- V1: Esquema inicial — MVP menús digitales SaaS

CREATE TABLE roles (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(50)  NOT NULL UNIQUE,
    description VARCHAR(255)
);

CREATE TABLE restaurants (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    slug        VARCHAR(120) NOT NULL UNIQUE,
    logo_url    VARCHAR(500),
    description TEXT,
    phone       VARCHAR(30),
    address     VARCHAR(255),
    whatsapp    VARCHAR(30),
    instagram   VARCHAR(120),
    facebook    VARCHAR(120),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT REFERENCES restaurants (id) ON DELETE SET NULL,
    name          VARCHAR(120) NOT NULL,
    email         VARCHAR(160) NOT NULL UNIQUE,
    password      VARCHAR(100) NOT NULL,
    role_id       BIGINT       NOT NULL REFERENCES roles (id),
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_users_restaurant ON users (restaurant_id);
CREATE INDEX idx_users_email ON users (email);

CREATE TABLE categories (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT       NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(255),
    position      INT          NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_categories_restaurant_name UNIQUE (restaurant_id, name)
);

CREATE INDEX idx_categories_restaurant ON categories (restaurant_id);

CREATE TABLE products (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT        NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    category_id   BIGINT        NOT NULL REFERENCES categories (id) ON DELETE CASCADE,
    name          VARCHAR(160)  NOT NULL,
    description   TEXT,
    price         NUMERIC(12,2) NOT NULL CHECK (price >= 0),
    image_url     VARCHAR(500),
    available     BOOLEAN       NOT NULL DEFAULT TRUE,
    position      INT           NOT NULL DEFAULT 0,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_restaurant ON products (restaurant_id);
CREATE INDEX idx_products_category ON products (category_id);

CREATE TABLE refresh_tokens (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT      NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token      VARCHAR(512) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens (user_id);

CREATE TABLE plans (
    id            BIGSERIAL PRIMARY KEY,
    code          VARCHAR(50)  NOT NULL UNIQUE,
    name          VARCHAR(120) NOT NULL,
    description   VARCHAR(255),
    price_monthly NUMERIC(12,2) NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE
);

CREATE TABLE subscriptions (
    id            BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT      NOT NULL REFERENCES restaurants (id) ON DELETE CASCADE,
    plan_id       BIGINT      NOT NULL REFERENCES plans (id),
    status        VARCHAR(30) NOT NULL DEFAULT 'ACTIVE',
    starts_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    ends_at       TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_subscriptions_restaurant ON subscriptions (restaurant_id);