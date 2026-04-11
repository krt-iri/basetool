-- Update existing amounts for PIECE materials to whole integers (floor)
UPDATE inventory_item
SET amount = FLOOR(amount)
FROM material m
WHERE inventory_item.material_id = m.id AND m.quantity_type = 'PIECE';
