-- A message passed on from another chat carries a "Forwarded" mark.
ALTER TABLE messages ADD COLUMN forwarded boolean NOT NULL DEFAULT false;
