-- Optional section heading on chunks (also applied by PgVectorStore.ensureSchema).

ALTER TABLE document_chunks
ADD COLUMN IF NOT EXISTS section_heading varchar(400);
