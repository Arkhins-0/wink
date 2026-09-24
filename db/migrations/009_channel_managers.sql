-- People an admin puts in charge of one race weekend's channel: they may
-- post there like an admin or coordinator would.
CREATE TABLE channel_managers (
  weekend_id uuid NOT NULL REFERENCES race_weekends(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  added_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (weekend_id, user_id)
);
CREATE INDEX channel_managers_user_idx ON channel_managers (user_id);
