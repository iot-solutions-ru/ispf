-- ADR-0063: deploy history provenance for Git Pull release.
ALTER TABLE application_bundle_deployments
    ADD COLUMN IF NOT EXISTS deploy_source VARCHAR(32);
ALTER TABLE application_bundle_deployments
    ADD COLUMN IF NOT EXISTS git_ref VARCHAR(255);
ALTER TABLE application_bundle_deployments
    ADD COLUMN IF NOT EXISTS git_sha VARCHAR(64);
