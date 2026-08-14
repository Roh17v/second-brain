-- Generated tsvector + GIN on document_chunks for keyword retrieval.
-- Also applied automatically by PgVectorStore.ensureSchema on startup.

ALTER TABLE document_chunks
ADD COLUMN IF NOT EXISTS content_tsv tsvector
GENERATED ALWAYS AS (
	setweight(to_tsvector('english', coalesce(content, '')), 'A')
	||
	setweight(to_tsvector('simple', coalesce(content, '')), 'B')
) STORED;

CREATE INDEX IF NOT EXISTS idx_document_chunks_content_tsv
ON document_chunks
USING GIN (content_tsv);
