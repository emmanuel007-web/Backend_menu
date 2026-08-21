-- V7: Endurecimiento de seguridad para usar Supabase solo como base de datos.
-- El backend accede directo a Postgres (dueño de las tablas, no afectado por RLS).
-- La API publica de Supabase (anon/authenticated) queda bloqueada:
-- RLS activada sin politicas = denegar todo por defecto.

-- Supabase aplica timeouts bajos por defecto; los elevamos para esta sesion.
SET statement_timeout = '120s';
SET lock_timeout = '10s';

-- flyway_schema_history queda fuera: es metadata interna de Flyway, y su
-- bloqueo durante redeploys solapados era la causa de fallos de migracion.
DO $$
DECLARE
  t record;
  attempts int;
BEGIN
  FOR t IN SELECT tablename FROM pg_tables
           WHERE schemaname = 'public'
             AND tablename <> 'flyway_schema_history'
  LOOP
    attempts := 0;
    LOOP
      attempts := attempts + 1;
      BEGIN
        EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY', t.tablename);
        EXIT;
      EXCEPTION WHEN lock_not_available OR query_canceled THEN
        IF attempts >= 6 THEN
          RAISE NOTICE 'Tabla % sigue bloqueada tras % intentos', t.tablename, attempts;
          RAISE;
        END IF;
        PERFORM pg_sleep(4);
      END;
    END LOOP;
  END LOOP;
END $$;

DO $$
DECLARE r text;
BEGIN
  FOREACH r IN ARRAY ARRAY['anon', 'authenticated'] LOOP
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = r) THEN
      EXECUTE format('REVOKE ALL ON ALL TABLES IN SCHEMA public FROM %I', r);
      EXECUTE format('REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM %I', r);
      EXECUTE format('REVOKE ALL ON SCHEMA public FROM %I', r);
      EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES FROM %I', r);
      EXECUTE format('ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON SEQUENCES FROM %I', r);
    END IF;
  END LOOP;
END $$;
