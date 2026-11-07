-- Indexes for the paged lists (orders, doctors, users, a user's own quick gifts): the default sort, and the search boxes.

-- The default order of each list, so a page is read from the index rather than sorted out of the whole table.
CREATE INDEX IF NOT EXISTS idx_orders_company_created ON orders (company_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_orders_vendor_created ON orders (assigned_vendor_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_recipients_company_name ON recipients (company_id, name);
CREATE INDEX IF NOT EXISTS idx_users_company_name ON users (company_id, name);
CREATE INDEX IF NOT EXISTS idx_quick_gift_requests_requester_created ON quick_gift_requests (requested_by, created_at DESC);

-- "Contains" searches (lower(col) LIKE '%text%') need trigram indexes to avoid reading every row. pg_trgm ships with Postgres; if the
-- database does not allow it, the lists still work, just slower on a very large table, so the migration does not fail over it.
DO $$
BEGIN
    CREATE EXTENSION IF NOT EXISTS pg_trgm;
EXCEPTION WHEN OTHERS THEN
    RAISE NOTICE 'pg_trgm is not available (%): skipping the search indexes', SQLERRM;
END
$$;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM pg_extension WHERE extname = 'pg_trgm') THEN
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_recipients_name_trgm ON recipients USING gin (lower(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_recipients_pcode_trgm ON recipients USING gin (lower(pcode) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_recipients_phone_trgm ON recipients USING gin (lower(phone) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_users_name_trgm ON users USING gin (lower(name) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_users_email_trgm ON users USING gin (lower(email) gin_trgm_ops)';
        EXECUTE 'CREATE INDEX IF NOT EXISTS idx_orders_recipient_name_trgm ON orders USING gin (lower(recipient_name) gin_trgm_ops)';
    END IF;
END
$$;
