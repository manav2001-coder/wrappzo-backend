-- Bootstrap seed: the minimal access-control catalog needed for the
-- platform Company/User management endpoints, plus the one Super Admin
-- account that creates every other company/user through the platform API.
-- This account is seeded directly (no invite flow) since it's the very
-- first account in the system — there's no one to invite it.

INSERT INTO access_controls (key, name, description, category, type) VALUES
    ('company.manage', 'Manage Companies', 'Create, edit, and activate/deactivate companies', 'Company', 'FUNCTIONAL'),
    ('user.manage', 'Manage Users', 'Create and manage users', 'User', 'FUNCTIONAL'),
    ('role.manage', 'Manage Roles', 'Create and edit roles and their access-control keys', 'Role', 'FUNCTIONAL');

WITH super_admin_role AS (
    INSERT INTO roles (company_id, name, description)
    VALUES (NULL, 'Super Admin', 'Wrapzzo platform staff -- full cross-company access')
    RETURNING id
)
INSERT INTO role_access_controls (role_id, access_control_id)
SELECT super_admin_role.id, access_controls.id
FROM super_admin_role, access_controls
WHERE access_controls.key IN ('company.manage', 'user.manage', 'role.manage');

-- Password: SuperAdmin@123 (dev/test seed only -- rotate before any real deployment).
INSERT INTO users (company_id, role_id, name, email, password_hash, status)
SELECT NULL, roles.id, 'Wrapzzo Super Admin', 'superadmin@wrapzzo.com',
       '$argon2id$v=19$m=16384,t=2,p=1$AWtrJ33ajR/3BFv/74eumg$Nou/6aXEPHCCDdQhSOxAcO3RvRObQlI3zGlxlF5WUUs',
       'ACTIVE'
FROM roles
WHERE roles.name = 'Super Admin' AND roles.company_id IS NULL;
