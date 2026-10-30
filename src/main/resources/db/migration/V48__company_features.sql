-- Feature master, per company: which permission keys a company has registered, and whether each is
-- switched on. A company's people can hold only its active features, whatever a template carries
-- (see AccessControlCacheService), and a company template may carry only those. New keys reach
-- existing companies at startup (CompanyFeatureRegistrar), new companies when they are created;
-- an admin who switches a feature off keeps it off, since registering only ever adds.
CREATE TABLE company_access_controls (
    company_id UUID NOT NULL REFERENCES companies (id) ON DELETE CASCADE,
    access_control_id UUID NOT NULL REFERENCES access_controls (id) ON DELETE CASCADE,
    is_active BOOLEAN NOT NULL DEFAULT true,
    PRIMARY KEY (company_id, access_control_id)
);
CREATE INDEX idx_company_access_controls_feature ON company_access_controls (access_control_id);

INSERT INTO company_access_controls (company_id, access_control_id)
SELECT c.id, ac.id FROM companies c CROSS JOIN access_controls ac WHERE ac.company_assignable;
