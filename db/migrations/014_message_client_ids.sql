-- The sending phone's own id for each message, and the batch it was sent in,
-- the way Telegram's random_id works:
--   client_id  lets the phone swap its temporary copy for the server's one in
--              place (never both on screen), and makes a resend after a lost
--              connection harmless: the same sender and client_id is the same
--              message.
--   batch_id   photos sent (or forwarded) together, so they stay one grid
--   batch_pos  each one's place in that batch, so the grid keeps its order
-- All optional: older apps and the website leave them empty.
ALTER TABLE messages
  ADD COLUMN client_id text,
  ADD COLUMN batch_id text,
  ADD COLUMN batch_pos int;

CREATE UNIQUE INDEX messages_sender_client_idx ON messages (sender_id, client_id) WHERE client_id IS NOT NULL;
