-- Seasons: every race weekend belongs to one, and every message is
-- stamped with the season it was sent in, so a whole season can be
-- archived (read-only, out of the live views) or deleted together.

CREATE TABLE seasons (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  starts_on date NOT NULL,
  ends_on date,
  status text NOT NULL DEFAULT 'active' CHECK (status IN ('active', 'archived')),
  archived_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Nullable on purpose: code deployed before this migration keeps writing
-- rows without a season, and the server treats NULL as "current".
ALTER TABLE race_weekends ADD COLUMN season_id uuid REFERENCES seasons(id) ON DELETE CASCADE;
ALTER TABLE messages ADD COLUMN season_id uuid REFERENCES seasons(id) ON DELETE CASCADE;
CREATE INDEX race_weekends_season_idx ON race_weekends (season_id);
CREATE INDEX messages_season_idx ON messages (season_id);

-- Everything so far goes into one season named after the current year.
DO $$
DECLARE
  first uuid;
BEGIN
  INSERT INTO seasons (name, starts_on)
  VALUES (to_char(now(), 'YYYY') || ' Season', date_trunc('year', now())::date)
  RETURNING id INTO first;
  UPDATE race_weekends SET season_id = first WHERE season_id IS NULL;
  UPDATE messages SET season_id = first WHERE season_id IS NULL;
END $$;
