-- Wink schema. Applied by `npm run migrate` (scripts/migrate.mjs), in order,
-- once each. Never edit an applied migration; add a new file.

-- Every account. The hierarchy is the parent_id chain: the person who
-- created the account (or, for a reassigned volunteer, the coordinator
-- they now belong to).
CREATE TABLE users (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  email text NOT NULL UNIQUE,
  password_hash text,
  role text NOT NULL CHECK (role IN (
    'admin', 'coordinator', 'race_official', 'team_manager', 'driver', 'crew',
    'security_head', 'security', 'volunteer'
  )),
  parent_id uuid REFERENCES users(id) ON DELETE SET NULL,
  team_name text,
  status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'active', 'suspended', 'dismissed', 'banned')),
  verify_code text NOT NULL UNIQUE,
  qr_token text NOT NULL UNIQUE,
  name text,
  dob date,
  phone text,
  photo_key text,
  profile_completed_at timestamptz,
  created_by uuid REFERENCES users(id) ON DELETE SET NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX users_parent_idx ON users (parent_id);
CREATE INDEX users_role_idx ON users (role);

-- Invite and password-reset links. Only the hash of the token is stored.
CREATE TABLE auth_tokens (
  hash text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  kind text NOT NULL CHECK (kind IN ('invite', 'reset')),
  expires_at timestamptz NOT NULL,
  used_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX auth_tokens_user_idx ON auth_tokens (user_id);

-- Signed-in sessions: a cookie on the web, a bearer token in the app.
CREATE TABLE sessions (
  hash text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform text NOT NULL DEFAULT 'web',
  expires_at timestamptz NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_seen_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sessions_user_idx ON sessions (user_id);

-- Firebase Cloud Messaging registration tokens, one row per device/browser.
CREATE TABLE push_tokens (
  token text PRIMARY KEY,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  platform text NOT NULL DEFAULT 'android',
  updated_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX push_tokens_user_idx ON push_tokens (user_id);

CREATE TABLE login_attempts (
  id bigserial PRIMARY KEY,
  email text NOT NULL,
  ip text NOT NULL,
  at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX login_attempts_email_idx ON login_attempts (email, at);

CREATE TABLE race_weekends (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  name text NOT NULL,
  venue text NOT NULL DEFAULT '',
  city text NOT NULL DEFAULT '',
  country text NOT NULL DEFAULT '',
  timezone text NOT NULL DEFAULT 'UTC',
  starts_on date NOT NULL,
  ends_on date NOT NULL,
  channel_open boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE race_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  weekend_id uuid NOT NULL REFERENCES race_weekends(id) ON DELETE CASCADE,
  name text NOT NULL,
  starts_at timestamptz NOT NULL,
  ends_at timestamptz NOT NULL,
  CHECK (ends_at > starts_at)
);
CREATE INDEX race_sessions_weekend_idx ON race_sessions (weekend_id, starts_at);
CREATE INDEX race_sessions_time_idx ON race_sessions (ends_at);

-- Documents and photos. `ready` flips once the bytes are in storage.
CREATE TABLE files (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  key text NOT NULL UNIQUE,
  name text NOT NULL,
  mime text NOT NULL,
  size bigint NOT NULL DEFAULT 0,
  uploaded_by uuid REFERENCES users(id) ON DELETE SET NULL,
  ready boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- A channel per race weekend, or a private chat a superior opened with
-- one person below them. One-off broadcasts have no conversation.
CREATE TABLE conversations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  kind text NOT NULL CHECK (kind IN ('channel', 'direct')),
  weekend_id uuid REFERENCES race_weekends(id) ON DELETE CASCADE,
  owner_id uuid REFERENCES users(id) ON DELETE CASCADE,
  member_id uuid REFERENCES users(id) ON DELETE CASCADE,
  created_at timestamptz NOT NULL DEFAULT now(),
  last_message_at timestamptz
);
CREATE UNIQUE INDEX conversations_weekend_idx ON conversations (weekend_id) WHERE kind = 'channel';
CREATE UNIQUE INDEX conversations_direct_idx ON conversations (owner_id, member_id) WHERE kind = 'direct';
CREATE INDEX conversations_member_idx ON conversations (member_id);

CREATE TABLE messages (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  conversation_id uuid REFERENCES conversations(id) ON DELETE CASCADE,
  sender_id uuid REFERENCES users(id) ON DELETE SET NULL,
  body text NOT NULL DEFAULT '',
  file_id uuid REFERENCES files(id) ON DELETE SET NULL,
  urgent boolean NOT NULL DEFAULT false,
  created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX messages_conversation_idx ON messages (conversation_id, created_at);

-- Who a message reached, and whether they have seen it. The inbox.
CREATE TABLE message_recipients (
  message_id uuid NOT NULL REFERENCES messages(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  read_at timestamptz,
  PRIMARY KEY (message_id, user_id)
);
CREATE INDEX message_recipients_user_idx ON message_recipients (user_id, read_at);

CREATE TABLE audit_log (
  id bigserial PRIMARY KEY,
  actor_id uuid REFERENCES users(id) ON DELETE SET NULL,
  target_id uuid,
  action text NOT NULL,
  detail jsonb,
  created_at timestamptz NOT NULL DEFAULT now()
);
