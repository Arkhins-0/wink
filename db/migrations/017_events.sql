-- Events, in groups and announcements: a name, when (and until when), where,
-- and a reminder, on a message; and each person's answer, going or not.
CREATE TABLE events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id uuid NOT NULL UNIQUE REFERENCES messages(id) ON DELETE CASCADE,
  name text NOT NULL,
  description text NOT NULL DEFAULT '',
  starts_at timestamptz NOT NULL,
  ends_at timestamptz,
  location text NOT NULL DEFAULT '',
  -- Minutes before the start that phones remind (null: no reminder; 0: at the start).
  reminder_minutes int,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX events_starts_idx ON events (starts_at);

CREATE TABLE event_replies (
  event_id uuid NOT NULL REFERENCES events(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  answer text NOT NULL CHECK (answer IN ('going', 'not_going')),
  updated_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (event_id, user_id)
);
