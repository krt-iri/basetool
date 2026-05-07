-- Macht die Geldfelder eines Raffinerieauftrags optional (nullable) und entfernt die NOT NULL/Default-Constraints.
-- Hintergrund: expenses, other_expenses und ore_sales sind im UI mit 0 vorbelegt, semantisch aber nicht
-- zwingend gesetzt. Beim Speichern wird 0 jetzt als "nicht gesetzt" behandelt und als NULL persistiert.
-- Die Profit-Berechnung (Service / RefineryOrder#getProfit) behandelt NULL bereits als 0, sodass die
-- Einsatz-Finanz-Aggregation unveraendert bleibt.

ALTER TABLE refinery_order ALTER COLUMN expenses DROP NOT NULL;
ALTER TABLE refinery_order ALTER COLUMN expenses DROP DEFAULT;

ALTER TABLE refinery_order ALTER COLUMN other_expenses DROP NOT NULL;
ALTER TABLE refinery_order ALTER COLUMN other_expenses DROP DEFAULT;

ALTER TABLE refinery_order ALTER COLUMN ore_sales DROP NOT NULL;
ALTER TABLE refinery_order ALTER COLUMN ore_sales DROP DEFAULT;

-- Bestehende Auftraege, die 0 enthalten, werden auf NULL umgestellt, damit "leer" und "0" einheitlich
-- als nicht gesetzt repraesentiert sind.
UPDATE refinery_order SET expenses = NULL WHERE expenses = 0;
UPDATE refinery_order SET other_expenses = NULL WHERE other_expenses = 0;
UPDATE refinery_order SET ore_sales = NULL WHERE ore_sales = 0;
