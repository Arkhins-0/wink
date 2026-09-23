-- Private-chat replies, edits and deletes. A deleted message keeps its row
-- (so the chat shows "This message was deleted") but loses its text and
-- file. changed_at moves on every edit or delete, so a phone that keeps
-- its own copy of a chat can ask for what changed, not only what is new.

ALTER TABLE messages ADD COLUMN reply_to_id uuid REFERENCES messages(id) ON DELETE SET NULL;
ALTER TABLE messages ADD COLUMN edited_at timestamptz;
ALTER TABLE messages ADD COLUMN deleted_at timestamptz;
ALTER TABLE messages ADD COLUMN changed_at timestamptz;
CREATE INDEX messages_changed_idx ON messages (conversation_id, changed_at) WHERE changed_at IS NOT NULL;
