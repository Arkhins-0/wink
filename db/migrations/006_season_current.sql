-- The current season is chosen, not implied. Until now the newest active
-- season was current, so making a new one moved everything at once; now
-- an admin makes a season current on purpose, and the one before it is
-- archived in the same step.
ALTER TABLE seasons ADD COLUMN is_current boolean NOT NULL DEFAULT false;
CREATE UNIQUE INDEX seasons_current_idx ON seasons (is_current) WHERE is_current;

-- Keep what was current until now: the newest active season.
UPDATE seasons SET is_current = true
WHERE id = (SELECT id FROM seasons WHERE status = 'active' ORDER BY starts_on DESC, created_at DESC LIMIT 1);
