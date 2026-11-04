-- A doctor someone added -- through a Quick Gift or a campaign entry rather than on the Doctors screen -- was
-- in the directory but never on their adder's own list. The Doctors screen shows a field user their list (the
-- doctors they added or are mapped to), so put each such doctor on the list of whoever added them.
INSERT INTO recipient_assignments (recipient_id, mr_user_id, assigned_at)
SELECT r.id, r.created_by, now()
FROM recipients r
WHERE r.created_by IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM recipient_assignments a WHERE a.recipient_id = r.id AND a.mr_user_id = r.created_by);
