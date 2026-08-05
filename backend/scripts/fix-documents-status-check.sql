-- Hibernate ddl-auto=update does NOT expand enum CHECK constraints when
-- DocumentStatus gains new values. Run this after adding EMBEDDING (or any status).
--
-- psql -h localhost -U postgres -d secondbrain -f scripts/fix-documents-status-check.sql

ALTER TABLE documents DROP CONSTRAINT IF EXISTS documents_status_check;

ALTER TABLE documents ADD CONSTRAINT documents_status_check
	CHECK (status IN ('UPLOADED', 'PROCESSING', 'EMBEDDING', 'READY', 'FAILED'));
