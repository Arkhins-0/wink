-- Link previews (WhatsApp's card for a link): what a page says about itself (Open Graph), fetched once by the server
-- and kept, and on each message the link it shows a preview of. A message sent with the preview closed has none.
CREATE TABLE link_previews (
  url text PRIMARY KEY,
  title text NOT NULL DEFAULT '',
  description text NOT NULL DEFAULT '',
  -- The page's picture, as the page gives it; shown only through /api/link-preview/image.
  image_url text,
  site_name text NOT NULL DEFAULT '',
  fetched_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX link_previews_image_idx ON link_previews (image_url);

ALTER TABLE messages ADD COLUMN link_url text;
