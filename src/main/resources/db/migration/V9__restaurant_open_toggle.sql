-- Interruptor abierto/cerrado por restaurante.
-- Cuando esta cerrado, la API rechaza nuevos pedidos.
ALTER TABLE restaurants ADD COLUMN IF NOT EXISTS open BOOLEAN NOT NULL DEFAULT TRUE;
