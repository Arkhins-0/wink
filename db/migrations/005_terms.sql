-- When each person agreed to the Terms and Conditions and the Privacy
-- Policy while setting up their account, and which version they agreed to.
ALTER TABLE users ADD COLUMN terms_accepted_at timestamptz;
ALTER TABLE users ADD COLUMN terms_version text;
