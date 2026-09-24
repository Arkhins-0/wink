-- Groups: a conversation of kind 'group' with a name, a photo, members
-- (some of them admins) and a rule for who may send. People join by
-- accepting an invitation, which reaches them as a message in their
-- private chat with whoever invited them.

ALTER TABLE conversations DROP CONSTRAINT conversations_kind_check;
ALTER TABLE conversations ADD CONSTRAINT conversations_kind_check CHECK (kind IN ('channel', 'direct', 'group'));
ALTER TABLE conversations
  ADD COLUMN name text,
  ADD COLUMN photo_key text,
  ADD COLUMN created_by uuid REFERENCES users(id) ON DELETE SET NULL,
  ADD COLUMN send_policy text NOT NULL DEFAULT 'everyone' CHECK (send_policy IN ('everyone', 'admins'));

CREATE TABLE group_members (
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  role text NOT NULL DEFAULT 'member' CHECK (role IN ('admin', 'member')),
  joined_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (conversation_id, user_id)
);
CREATE INDEX group_members_user_idx ON group_members (user_id);

CREATE TABLE group_invites (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid NOT NULL REFERENCES conversations(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  invited_by uuid REFERENCES users(id) ON DELETE SET NULL,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'accepted', 'declined')),
  created_at timestamptz NOT NULL DEFAULT now(),
  answered_at timestamptz
);
CREATE UNIQUE INDEX group_invites_pending_idx ON group_invites (conversation_id, user_id) WHERE status = 'pending';
CREATE INDEX group_invites_user_idx ON group_invites (user_id);

-- The message that carries an invitation.
ALTER TABLE messages ADD COLUMN group_invite_id uuid REFERENCES group_invites(id) ON DELETE SET NULL;
