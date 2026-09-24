-- A group admin can take back an invitation that has not been answered.
ALTER TABLE group_invites DROP CONSTRAINT group_invites_status_check;
ALTER TABLE group_invites ADD CONSTRAINT group_invites_status_check
  CHECK (status IN ('pending', 'accepted', 'declined', 'expired', 'revoked'));
