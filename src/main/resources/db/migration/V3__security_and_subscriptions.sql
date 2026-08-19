-- V3: Seguridad y ciclo de vida de suscripciones
-- 1) Suscripciones: proveedor de pago + referencia externa (Stripe).
-- 2) Normalizar URLs de imágenes almacenadas: se guarda solo el fileId
--    (antes se guardaba "/uploads/<uuid>.ext"); el acceso es por URL firmada.

ALTER TABLE subscriptions
    ADD COLUMN provider           VARCHAR(20)  NOT NULL DEFAULT 'MANUAL',
    ADD COLUMN provider_reference VARCHAR(255);

UPDATE products SET image_url = regexp_replace(image_url, '^/uploads/', '')
    WHERE image_url LIKE '/uploads/%';

UPDATE restaurants SET logo_url = regexp_replace(logo_url, '^/uploads/', '')
    WHERE logo_url LIKE '/uploads/%';