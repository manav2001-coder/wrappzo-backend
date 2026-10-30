-- Feature master: a permission key can be switched off for everyone without a deploy. An inactive
-- key grants nothing wherever a template carries it (see AccessControlCacheService).
ALTER TABLE access_controls ADD COLUMN is_active BOOLEAN NOT NULL DEFAULT true;
