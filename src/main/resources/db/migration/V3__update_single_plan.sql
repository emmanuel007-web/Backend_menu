-- V3: Plan único NegoCode ($49.900/mes) con acceso total

-- Desactivar planes anteriores si existen
UPDATE plans SET active = FALSE WHERE code IN ('FREE', 'BASIC', 'PRO', 'PREMIUM');

-- Insertar o actualizar el plan único NegoCode
INSERT INTO plans (code, name, description, price_monthly, active)
VALUES (
    'NEGOCODE',
    'Plan NegoCode',
    'Acceso total a la plataforma: menú digital ilimitado, código QR, recepción de pedidos en tiempo real y administración completa.',
    49900,
    TRUE
)
ON CONFLICT (code) DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    price_monthly = EXCLUDED.price_monthly,
    active = TRUE;

-- Actualizar suscripciones existentes para que apunten al nuevo plan NEGOCODE
UPDATE subscriptions
SET plan_id = (SELECT id FROM plans WHERE code = 'NEGOCODE')
WHERE status = 'ACTIVE';
