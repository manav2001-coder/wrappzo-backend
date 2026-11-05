-- Quick Gift has no approval step: a request goes straight to the vendor whose gift was picked. Remove its key.
DELETE FROM template_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'quick_gift.approve');
DELETE FROM company_access_controls WHERE access_control_id IN (SELECT id FROM access_controls WHERE key = 'quick_gift.approve');
DELETE FROM access_controls WHERE key = 'quick_gift.approve';
