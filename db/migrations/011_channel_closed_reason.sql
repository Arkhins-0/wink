-- Why a race weekend's channel is closed: 'admin' (closed by hand) or
-- 'season' (its season was archived). Bringing a season back reopens the
-- channels its archiving closed, and only those.
ALTER TABLE race_weekends ADD COLUMN channel_closed_reason text CHECK (channel_closed_reason IN ('admin', 'season'));

-- Every channel closed so far was closed by archiving its season (the audit log has no closing by hand).
UPDATE race_weekends SET channel_closed_reason = 'season' WHERE NOT channel_open;
