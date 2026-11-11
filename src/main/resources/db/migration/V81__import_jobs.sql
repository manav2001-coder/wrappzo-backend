-- A big sheet (users, doctors) is checked in the request but saved in the background, so the request does not hold a thread, a
-- database connection and one long transaction open. The rows wait here until a worker takes the job; the screen polls it.
-- The rows are personal data, so they are cleared as soon as the job has finished.
CREATE TABLE import_jobs (
    id            UUID PRIMARY KEY,
    kind          VARCHAR(20)  NOT NULL,             -- USERS, DOCTORS
    company_id    UUID,
    requested_by  UUID         NOT NULL,
    status        VARCHAR(20)  NOT NULL,             -- QUEUED, RUNNING, COMPLETED, FAILED
    total_rows    INT          NOT NULL,
    payload       TEXT,
    result        TEXT,                              -- JSON counts when COMPLETED
    errors        TEXT,                              -- JSON array of row problems when FAILED
    message       VARCHAR(1000),
    attempts      INT          NOT NULL DEFAULT 0,
    claimed_until TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ
);

CREATE INDEX idx_import_jobs_open ON import_jobs (created_at) WHERE status IN ('QUEUED', 'RUNNING');
CREATE INDEX idx_import_jobs_finished ON import_jobs (finished_at) WHERE status IN ('COMPLETED', 'FAILED');
