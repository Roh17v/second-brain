package com.secondbrain.search;

import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL + pgvector implementation.
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
						SELECT id, document_id, chunk_index, content,
						       (1 - (embedding <=> CAST(? AS vector))) AS score
						FROM document_chunks
						WHERE workspace_id = ?
						  AND embedding IS NOT NULL
						ORDER BY embedding <=> CAST(? AS vector)
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
