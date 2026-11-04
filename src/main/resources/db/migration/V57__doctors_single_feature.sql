-- One Doctors feature. "My Doctors" was a second screen with its own keys; it is now the same Doctors
-- screen, and what a person does there is decided by the doctor.* keys:
--   doctor.view     see the doctors in reach (their own and mapped ones; managers: their org units)
--   doctor.create   add a doctor (to their own list)
--   doctor.edit     change a doctor's details
--   doctor.remove   take a doctor off their own list          (new)
--   doctor.import   bulk upload a sheet of doctors            (new)
--   doctor.approve  review doctor change requests             (was my_doctors.approve)
--
-- Whoever held my_doctors.manage keeps everything they could do: they now hold view, create, edit, remove
-- and import. Grants of my_doctors.approve follow the renamed key.

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('doctor.remove', 'doctor', 'remove', 'Doctors', 'Remove', 'Take a doctor off your own list', 'FUNCTIONAL', true, 703),
    ('doctor.import', 'doctor', 'import', 'Doctors', 'Bulk upload', 'Add many doctors at once from a sheet', 'FUNCTIONAL', true, 704);

UPDATE access_controls
SET key = 'doctor.approve', module = 'doctor', action = 'approve', category = 'Doctors', sort_order = 705, updated_at = now()
WHERE key = 'my_doctors.approve';

CREATE TEMPORARY TABLE doctor_keys (key VARCHAR(150) NOT NULL);
INSERT INTO doctor_keys (key) VALUES ('doctor.view'), ('doctor.create'), ('doctor.edit'), ('doctor.remove'), ('doctor.import');

INSERT INTO template_access_controls (template_id, access_control_id)
SELECT g.template_id, nk.id
FROM template_access_controls g
JOIN access_controls old ON old.id = g.access_control_id AND old.key = 'my_doctors.manage'
JOIN access_controls nk ON nk.key IN (SELECT key FROM doctor_keys)
ON CONFLICT DO NOTHING;

INSERT INTO role_access_controls (role_id, access_control_id)
SELECT g.role_id, nk.id
FROM role_access_controls g
JOIN access_controls old ON old.id = g.access_control_id AND old.key = 'my_doctors.manage'
JOIN access_controls nk ON nk.key IN (SELECT key FROM doctor_keys)
ON CONFLICT DO NOTHING;

INSERT INTO designation_access_controls (company_id, designation, access_control_id)
SELECT g.company_id, g.designation, nk.id
FROM designation_access_controls g
JOIN access_controls old ON old.id = g.access_control_id AND old.key = 'my_doctors.manage'
JOIN access_controls nk ON nk.key IN (SELECT key FROM doctor_keys)
ON CONFLICT DO NOTHING;

INSERT INTO user_access_controls (user_id, access_control_id, effect, granted_by)
SELECT g.user_id, nk.id, g.effect, g.granted_by
FROM user_access_controls g
JOIN access_controls old ON old.id = g.access_control_id AND old.key = 'my_doctors.manage'
JOIN access_controls nk ON nk.key IN (SELECT key FROM doctor_keys)
ON CONFLICT DO NOTHING;

INSERT INTO company_access_controls (company_id, access_control_id, is_active)
SELECT g.company_id, nk.id, g.is_active
FROM company_access_controls g
JOIN access_controls old ON old.id = g.access_control_id AND old.key = 'my_doctors.manage'
JOIN access_controls nk ON nk.key IN (SELECT key FROM doctor_keys)
ON CONFLICT DO NOTHING;

-- Its grants are copied; deleting the key cascades the originals away.
DELETE FROM access_controls WHERE key = 'my_doctors.manage';
