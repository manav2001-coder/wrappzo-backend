-- Who added and last changed a company, vendor, role or user (the Masters' audit columns). The
-- dates already exist as created_at / updated_at. Stamped by AuditNameListener from the signed-in
-- user; rows that predate this stay blank except vendors, which already record who created them.
ALTER TABLE companies ADD COLUMN created_by_name VARCHAR(200);
ALTER TABLE companies ADD COLUMN updated_by_name VARCHAR(200);
ALTER TABLE vendors ADD COLUMN created_by_name VARCHAR(200);
ALTER TABLE vendors ADD COLUMN updated_by_name VARCHAR(200);
ALTER TABLE roles ADD COLUMN created_by_name VARCHAR(200);
ALTER TABLE roles ADD COLUMN updated_by_name VARCHAR(200);
ALTER TABLE users ADD COLUMN created_by_name VARCHAR(200);
ALTER TABLE users ADD COLUMN updated_by_name VARCHAR(200);

UPDATE vendors v SET created_by_name = u.name FROM users u WHERE u.id = v.created_by;
UPDATE vendors v SET updated_by_name = u.name FROM users u WHERE u.id = v.updated_by;
