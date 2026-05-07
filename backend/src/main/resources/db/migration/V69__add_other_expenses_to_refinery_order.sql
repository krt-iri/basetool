-- Fuegt das Feld other_expenses (Sonstige Kosten) zum Raffinerieauftrag hinzu.
-- Bestehende Datensaetze erhalten den Wert 0, damit keine NPE/Rechenfehler in der Einsatz-Finanz-Aggregation entstehen.

ALTER TABLE refinery_order
    ADD COLUMN IF NOT EXISTS other_expenses DOUBLE PRECISION NOT NULL DEFAULT 0;

UPDATE refinery_order SET other_expenses = 0 WHERE other_expenses IS NULL;

COMMENT ON COLUMN refinery_order.other_expenses IS 'Sonstige Kosten zusaetzlich zu expenses. Zahl >= 0, Default 0.';
