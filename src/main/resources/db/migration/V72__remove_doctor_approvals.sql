-- Doctors are added, edited and removed directly; there is no approval step. Remove the change-request flow and its key.
DELETE FROM template_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.approve');
DELETE FROM company_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'doctor.approve');
DELETE FROM access_controls WHERE key = 'doctor.approve';
DROP TABLE IF EXISTS doctor_change_requests;
