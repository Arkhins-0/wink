-- A message can carry many attachments: up to 30 photos plus documents and
-- audio, in the order they were picked. messages.file_id stays as the first
-- one, so older app versions (and anything reading only one file) still
-- see something.
CREATE TABLE message_files (
  message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  file_id uuid NOT NULL REFERENCES files(id) ON DELETE CASCADE,
  position int NOT NULL DEFAULT 0,
  PRIMARY KEY (message_id, file_id)
);
CREATE INDEX message_files_message_idx ON message_files (message_id, position);

-- Every message so far that has a file gets it as its one attachment.
INSERT INTO message_files (message_id, file_id, position)
SELECT id, file_id, 0 FROM messages WHERE file_id IS NOT NULL
ON CONFLICT DO NOTHING;
