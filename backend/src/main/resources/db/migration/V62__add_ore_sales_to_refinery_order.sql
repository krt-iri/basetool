-- Fuegt das Feld ore_sales (Einnahmen aus dem Verkauf roher Erze) zum Raffinerieauftrag hinzu.
-- Bestehende Datensaetze erhalten den Wert 0, damit keine NPE/Rechenfehler in der Einsatz-Finanz-Aggregation entstehen.

ALTER TABLE refinery_order
    ADD COLUMN IF NOT EXISTS ore_sales DOUBLE PRECISION NOT NULL DEFAULT 0;

UPDATE refinery_order SET ore_sales = 0 WHERE ore_sales IS NULL;

COMMENT ON COLUMN refinery_order.ore_sales IS 'Einnahmen durch den Verkauf roher Erze (Ore Sales). Ganzzahl >= 0, Default 0.';
