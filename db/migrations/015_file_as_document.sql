-- A file sent through "Document" stays a document for everyone, whatever it
-- is: a photo isn't shrunk or shown in a grid, audio isn't turned into a
-- player. Its own type (image/jpeg, audio/mpeg…) is kept for opening it.
ALTER TABLE files ADD COLUMN as_document boolean NOT NULL DEFAULT false;
