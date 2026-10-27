-- The brand is spelled Wrappzo. Fix the seeded super admin's display name and the platform
-- role's description where they still read as seeded (the login email stays as it is).
UPDATE users SET name = 'Wrappzo Super Admin' WHERE name = 'Wrapzzo Super Admin';
UPDATE roles SET description = 'Wrappzo platform staff -- full cross-company access'
WHERE company_id IS NULL AND description = 'Wrapzzo platform staff -- full cross-company access';
