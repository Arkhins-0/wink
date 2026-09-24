-- Group invitations work once and lapse after two days; one sent to
-- someone higher up is a join request rather than an invitation. Group
-- chats also carry event lines (joined, left, removed, …) as messages
-- with `event` set.
ALTER TABLE group_invites DROP CONSTRAINT group_invites_status_check;
ALTER TABLE group_invites ADD CONSTRAINT group_invites_status_check CHECK (status IN ('pending', 'accepted', 'declined', 'expired'));
ALTER TABLE group_invites ADD COLUMN expires_at timestamptz NOT NULL DEFAULT now() + interval '2 days';
ALTER TABLE group_invites ADD COLUMN upward boolean NOT NULL DEFAULT false;
UPDATE group_invites SET expires_at = created_at + interval '2 days';

ALTER TABLE messages ADD COLUMN event text;
