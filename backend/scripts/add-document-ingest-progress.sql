-- Progress + email-when-ready columns for document ingest.
-- Apply on production BEFORE restarting the jar (ddl-auto: validate).

ALTER TABLE documents
	ADD COLUMN IF NOT EXISTS chunk_count integer;

ALTER TABLE documents
	ADD COLUMN IF NOT EXISTS embedded_count integer NOT NULL DEFAULT 0;

ALTER TABLE documents
	ADD COLUMN IF NOT EXISTS notify_on_ready boolean NOT NULL DEFAULT false;

ALTER TABLE documents
	ADD COLUMN IF NOT EXISTS ready_notified_at timestamp with time zone;

ALTER TABLE documents
	ADD COLUMN IF NOT EXISTS processing_started_at timestamp with time zone;
