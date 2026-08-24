-- V8: Refresh tokens de vida corta, hasheados y con tope absoluto de sesion.
-- 1) Nueva columna session_expires_at: fijada en el login, heredada sin
--    extender por cada rotacion (tope absoluto de la sesion).
-- 2) Los tokens ahora se almacenan SOLO como SHA-256; las filas existentes
--    quedan invalidas -> todos los usuarios inician sesion nuevamente.

ALTER TABLE refresh_tokens
    ADD COLUMN IF NOT EXISTS session_expires_at TIMESTAMPTZ NOT NULL DEFAULT now();

DELETE FROM refresh_tokens;
