-- A record of every run of a scheduled background job, for the Platform config > Background jobs
-- screen: when it ran, who started it (the schedule, or a Super Admin retrying it), how it ended.
CREATE TABLE job_runs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    job_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    triggered_by VARCHAR(200) NOT NULL,
    attempts INT NOT NULL DEFAULT 1,
    started_at TIMESTAMPTZ NOT NULL,
    finished_at TIMESTAMPTZ,
    result TEXT,
    error TEXT
);
CREATE INDEX idx_job_runs_started ON job_runs (started_at DESC);
CREATE INDEX idx_job_runs_job_started ON job_runs (job_name, started_at DESC);

INSERT INTO access_controls (key, module, action, category, name, description, type, company_assignable, sort_order) VALUES
    ('platform_jobs.view', 'platform_jobs', 'view', 'Background jobs', 'View', 'See the background jobs and how each run went', 'FUNCTIONAL', false, 2500),
    ('platform_jobs.manage', 'platform_jobs', 'manage', 'Background jobs', 'Manage', 'Run a failed background job again, and clear finished runs', 'FUNCTIONAL', false, 2501);
