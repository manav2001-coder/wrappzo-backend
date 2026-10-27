-- V35 required an ADD request to never carry a recipient_id, but approving one creates (or
-- reuses) the doctor and records it on the request (DoctorChangeRequestService.applyAdd) -- so
-- every ADD approval failed this check. An ADD may carry its recipient once approved.
ALTER TABLE doctor_change_requests DROP CONSTRAINT chk_doctor_change_requests_recipient_linkage;
ALTER TABLE doctor_change_requests ADD CONSTRAINT chk_doctor_change_requests_recipient_linkage CHECK (
    (action = 'ADD' AND (recipient_id IS NULL OR status = 'APPROVED'))
    OR (action IN ('EDIT', 'REMOVE') AND recipient_id IS NOT NULL)
);
