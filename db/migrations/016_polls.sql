-- Polls, in groups and announcements: a question on a message, its options in
-- order, and who picked which. One vote per person per option; a poll that
-- allows only one answer keeps one row per person (the app replaces it).
CREATE TABLE polls (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  message_id uuid NOT NULL UNIQUE REFERENCES messages(id) ON DELETE CASCADE,
  question text NOT NULL,
  multiple boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE poll_options (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  poll_id uuid NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
  position int NOT NULL,
  text text NOT NULL
);
CREATE INDEX poll_options_poll_idx ON poll_options (poll_id, position);

CREATE TABLE poll_votes (
  poll_id uuid NOT NULL REFERENCES polls(id) ON DELETE CASCADE,
  option_id uuid NOT NULL REFERENCES poll_options(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (option_id, user_id)
);
CREATE INDEX poll_votes_poll_user_idx ON poll_votes (poll_id, user_id);
