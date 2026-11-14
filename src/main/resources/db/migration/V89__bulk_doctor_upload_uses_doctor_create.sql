-- Uploading a sheet of doctors is adding doctors, so it is allowed by the key that allows adding one: doctor.create.
-- The separate doctor.import key goes. Anyone who held import but not create keeps what they could do (the right to add
-- doctors, which a bulk upload was only ever a faster way of using) by being given create; then every import grant and
-- the key itself are removed. A user-level REVOKE of import is dropped with the key (they can bulk upload exactly as
-- they can add one).

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT g.template_id, c.id
FROM template_access_controls g
JOIN access_controls i ON i.id = g.access_control_id AND i.key = 'doctor.import'
JOIN access_controls c ON c.key = 'doctor.create'
ON CONFLICT DO NOTHING;

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT g.role_id, c.id
FROM role_access_controls g
JOIN access_controls i ON i.id = g.access_control_id AND i.key = 'doctor.import'
JOIN access_controls c ON c.key = 'doctor.create'
ON CONFLICT DO NOTHING;

INSERT INTO designation_access_controls (company_id, designation, access_control_id)
SELECT g.company_id, g.designation, c.id
FROM designation_access_controls g
JOIN access_controls i ON i.id = g.access_control_id AND i.key = 'doctor.import'
JOIN access_controls c ON c.key = 'doctor.create'
ON CONFLICT DO NOTHING;

INSERT INTO user_access_controls (user_id, access_control_id, effect, granted_by)
SELECT g.user_id, c.id, g.effect, g.granted_by
FROM user_access_controls g
JOIN access_controls i ON i.id = g.access_control_id AND i.key = 'doctor.import'
JOIN access_controls c ON c.key = 'doctor.create'
WHERE g.effect = 'GRANT'
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT g.company_id, c.id, g.is_active
FROM company_access_controls g
JOIN access_controls i ON i.id = g.access_control_id AND i.key = 'doctor.import'
JOIN access_controls c ON c.key = 'doctor.create'
ON CONFLICT DO NOTHING;

DELETE FROM role_access_controls        WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.import');
DELETE FROM template_access_controls    WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.import');
DELETE FROM designation_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.import');
DELETE FROM user_access_controls        WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.import');
DELETE FROM company_access_controls     WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.import');
DELETE FROM access_controls WHERE key = 'doctor.import';
