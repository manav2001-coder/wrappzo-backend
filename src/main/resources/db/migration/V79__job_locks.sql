-- One row per scheduled job, so that when the app runs on more than one replica only one of them runs a job at a time.
-- A replica takes the row by moving locked_until into the future; it is the database's clock that decides, never the replicas'.
CREATE TABLE job_locks (
    name         VARCHAR(100) PRIMARY KEY,
    locked_at    TIMESTAMPTZ  NOT NULL,
    locked_until TIMESTAMPTZ  NOT NULL,
    locked_by    VARCHAR(200) NOT NULL
);
