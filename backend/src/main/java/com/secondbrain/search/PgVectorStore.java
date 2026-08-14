package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL + pgvector implementation (dense cosine + generated tsvector FTS).
 * Active for non-test profiles (requires real Postgres with pgvector).
 */
@Repository
@Profile("!test")
public class PgVectorStore implements VectorStore {

	private static final Logger log = LoggerFactory.getLogger(PgVectorStore.class);

	private final JdbcTemplate jdbcTemplate;

	public PgVectorStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void ensureSchema(int dimensions) {
		jdbcTemplate.execute("CREATE EXTENSION IF NOT EXISTS vector");

		jdbcTemplate.execute("""
				ALTER TABLE document_chunks
				ADD COLUMN IF NOT EXISTS embedding vector(%d)
				""".formatted(dimensions));

		jdbcTemplate.execute("""
				ALTER TABLE document_chunks
				ADD COLUMN IF NOT EXISTS embedding_model varchar(120)
				""");

		jdbcTemplate.execute("""
				ALTER TABLE document_chunks
				ADD COLUMN IF NOT EXISTS section_heading varchar(400)
				""");

		try {
			jdbcTemplate.execute("""
					CREATE INDEX IF NOT EXISTS idx_document_chunks_embedding
					ON document_chunks
					USING hnsw (embedding vector_cosine_ops)
					""");
		}
		catch (Exception ex) {
			log.warn("Could not create HNSW index yet (often fine on empty tables): {}", ex.getMessage());
		}

		try {
			jdbcTemplate.execute("""
					ALTER TABLE document_chunks
					ADD COLUMN IF NOT EXISTS content_tsv tsvector
					GENERATED ALWAYS AS (
						setweight(to_tsvector('english', coalesce(content, '')), 'A')
						||
						setweight(to_tsvector('simple', coalesce(content, '')), 'B')
					) STORED
					""");
			jdbcTemplate.execute("""
					CREATE INDEX IF NOT EXISTS idx_document_chunks_content_tsv
					ON document_chunks
					USING GIN (content_tsv)
					""");
			log.info("pgvector FTS (content_tsv) ready");
		}
		catch (Exception ex) {
			log.warn("Could not create chunk FTS column/index (keyword search will no-op): {}", ex.getMessage());
		}

		log.info("pgvector schema ready (dimensions={})", dimensions);
	}

	@Override
	public void saveEmbedding(UUID chunkId, float[] embedding, String modelId) {
		String literal = toVectorLiteral(embedding);
		jdbcTemplate.update(
				"""
						UPDATE document_chunks
						SET embedding = CAST(? AS vector), embedding_model = ?
						WHERE id = ?
						""",
				literal,
				modelId,
				chunkId
		);
	}

	@Override
	public boolean hasEmbedding(UUID chunkId) {
		Boolean present = jdbcTemplate.queryForObject(
				"""
						SELECT embedding IS NOT NULL
						FROM document_chunks
						WHERE id = ?
						""",
				Boolean.class,
				chunkId
		);
		return Boolean.TRUE.equals(present);
	}

	@Override
	public long countEmbeddedChunks(UUID documentId) {
		Long count = jdbcTemplate.queryForObject(
				"""
						SELECT COUNT(*)
						FROM document_chunks
						WHERE document_id = ?
						  AND embedding IS NOT NULL
						""",
				Long.class,
				documentId
		);
		return count == null ? 0L : count;
	}

	@Override
	public List<ScoredChunk> similaritySearch(UUID workspaceId, float[] queryEmbedding, int topK) {
		String literal = toVectorLiteral(queryEmbedding);
		return jdbcTemplate.query(
				"""
						SELECT c.id, c.document_id, c.chunk_index, c.content,
						       (1 - (c.embedding <=> CAST(? AS vector))) AS score
						FROM document_chunks c
						INNER JOIN documents d ON d.id = c.document_id
						WHERE c.workspace_id = ?
						  AND c.embedding IS NOT NULL
						  AND d.deleted_at IS NULL
						  AND d.status = 'READY'
						ORDER BY c.embedding <=> CAST(? AS vector)
						LIMIT ?
						""",
				(rs, rowNum) -> new ScoredChunk(
						(UUID) rs.getObject("id"),
						(UUID) rs.getObject("document_id"),
						rs.getInt("chunk_index"),
						rs.getString("content"),
						rs.getDouble("score")
				),
				literal,
				workspaceId,
				literal,
				topK
		);
	}

	@Override
	public List<ScoredChunk> keywordSearch(UUID workspaceId, String query, int topK) {
		if (query == null || query.isBlank()) {
			return List.of();
		}
		String q = query.strip();
		if (q.length() > 500) {
			q = q.substring(0, 500);
		}
		String tsQuery = FullTextQuery.orTsQuery(q);
		if (tsQuery.isBlank()) {
			return List.of();
		}
		int k = topK <= 0 ? 5 : topK;
		try {
			return jdbcTemplate.query(
					"""
							SELECT c.id, c.document_id, c.chunk_index, c.content,
							       ts_rank_cd(c.content_tsv, q.query) AS score
							FROM document_chunks c
							INNER JOIN documents d ON d.id = c.document_id
							CROSS JOIN LATERAL (
								SELECT NULLIF(
									to_tsquery('english', ?) || to_tsquery('simple', ?),
									''::tsquery
								) AS query
							) q
							WHERE c.workspace_id = ?
							  AND c.embedding IS NOT NULL
							  AND c.content_tsv IS NOT NULL
							  AND d.deleted_at IS NULL
							  AND d.status = 'READY'
							  AND q.query IS NOT NULL
							  AND c.content_tsv @@ q.query
							ORDER BY score DESC, c.chunk_index ASC
							LIMIT ?
							""",
					(rs, rowNum) -> new ScoredChunk(
							(UUID) rs.getObject("id"),
							(UUID) rs.getObject("document_id"),
							rs.getInt("chunk_index"),
							rs.getString("content"),
							rs.getDouble("score")
					),
					tsQuery,
					tsQuery,
					workspaceId,
					k
			);
		}
		catch (Exception ex) {
			log.warn("Keyword/FTS search failed (dense retrieval still runs): {}", ex.getMessage());
			return List.of();
		}
	}

	static String toVectorLiteral(float[] embedding) {
		StringBuilder sb = new StringBuilder(embedding.length * 8);
		sb.append('[');
		for (int i = 0; i < embedding.length; i++) {
			if (i > 0) {
				sb.append(',');
			}
			sb.append(embedding[i]);
		}
		sb.append(']');
		return sb.toString();
	}
}
