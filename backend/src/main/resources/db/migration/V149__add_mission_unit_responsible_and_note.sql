-- Mission-detail tab layout (Variante B), unit modal per the approved mock:
-- a unit gets an explicit responsible person (previously only derivable from the
-- assigned ship's owner) and a free-text note ("z. B. Eskorte, Gruppe 1").
-- Both nullable — existing units keep their behaviour (responsible falls back to
-- the ship owner in the UI); no data backfill needed.
ALTER TABLE mission_unit
    ADD COLUMN responsible_user_id UUID REFERENCES app_user (id) ON DELETE SET NULL;
ALTER TABLE mission_unit
    ADD COLUMN note TEXT;
