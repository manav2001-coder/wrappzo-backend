-- The S3 object key for a company's logo, not a full URL -- the public URL
-- is constructed from FileStorageService's configured base URL, so this
-- stays correct even if that base URL (bucket, CDN domain, etc.) changes.
ALTER TABLE companies ADD COLUMN logo_key VARCHAR(500);
