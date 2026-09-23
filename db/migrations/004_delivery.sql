-- Ticks in private chats: one when the server has the message, two when
-- the other person's phone or browser has fetched it, three once read.
ALTER TABLE message_recipients ADD COLUMN delivered_at timestamptz;
UPDATE message_recipients SET delivered_at = read_at WHERE read_at IS NOT NULL;
