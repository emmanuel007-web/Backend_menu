-- V9: Interruptor abierto/cerrado por restaurante.
-- El dueño lo apaga para dejar de recibir pedidos; la API rechaza
-- nuevos pedidos mientras el restaurante este cerrado.

-- Supabase aplica timeouts bajos por defecto (misma causa de fallo que V7);
-- los elevamos para esta sesion y reintentamos ante bloqueos de redeploy.
SET statement_timeout = '120s';
SET lock_timeout = '10s';

DO $$
DECLARE
  attempts int;
BEGIN
  attempts := 0;
  LOOP
    attempts := attempts + 1;
    BEGIN
      ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS open BOOLEAN NOT NULL DEFAULT TRUE;
      EXIT;
    EXCEPTION WHEN lock_not_available OR query_canceled THEN
      IF attempts >= 6 THEN
        RAISE NOTICE 'Tabla restaurants sigue bloqueada tras % intentos', attempts;
        RAISE;
      END IF;
      PERFORM pg_sleep(4);
    END;
  END LOOP;
END $$;
