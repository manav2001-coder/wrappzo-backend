-- UserService.makeCompanyAdmin now propagates Company Admin's grants onto the promoted
-- user's designation (see DesignationAccessService.grantAccessControls) so anyone sharing
-- that designation inherits the same access -- but that only fires going forward, on the
-- makeCompanyAdmin call itself. Every user who was already Company Admin before this shipped
-- needs the same backfilled onto their designation, one time, here.
INSERT INTO designation_access_controls (company_id, designation, access_control_id)
SELECT DISTINCT u.company_id, u.designation, rac.access_control_id
FROM users u
JOIN roles r ON r.id = u.role_id AND r.company_id = u.company_id AND r.name = 'Company Admin'
JOIN role_access_controls rac ON rac.role_id = r.id
WHERE u.designation IS NOT NULL
ON CONFLICT (company_id, designation, access_control_id) DO NOTHING;
