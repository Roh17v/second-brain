package com.secondbrain.search;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Test-profile vector store: keeps embeddings in memory and scores with cosine similarity.
 * Chunk text is loaded from the H2 database via JDBC.
 */
@Repository
@Profile("test")
public class InMemoryVectorStore implements VectorStore {

	private final JdbcTemplate jdbcTemplate;
	private final Map<UUID, float[]> embeddings = new ConcurrentHashMap<>();
	private final Map<UUID, String> models = new ConcurrentHashMap<>();

	public InMemoryVectorStore(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Override
	public void ensureSchema(int dimensions) {
		// No-op for H2 tests.
	}

	@Override
	public void saveEmbedding(UUID chunkId, float[] embedding, String modelId) {
		embeddings.put(chunkId, embedding);
		models.put(chunkId, modelId);
	}

	@Override
	public boolean hasEmbedding(UUID chunkId) {
		return embeddings.containsKey(chunkId);
	}

	@Override
	public long countEmbeddedChunks(UUID documentId) {
		List<UUID> ids = jdbcTemplate.query(
				"SELECT id FROM document_chunks WHERE document_id = ?",
				(rs, rowNum) -> (UUID) rs.getObject(1),
				documentId
		);
		return ids.stream().filter(embeddings::containsKey).count();
	}

	@Override
	public List<ScoredChunk> similaritySearch(UUID workspaceId, float[] queryEmbedding, int topK) {
		List<ScoredChunk> all = jdbcTemplate.query(
				"""
						SELECT id, document_id, chunk_index, content
						FROM document_chunks
						WHERE workspace_id = ?
						""",
				(rs, rowNum) -> {
					UUID id = (UUID) rs.getObject("id");
					float[] vector = embeddings.get(id);
					double score = vector == null ? -1.0 : cosine(queryEmbedding, vector);
					return new ScoredChunk(
							id,
							(UUID) rs.getObject("document_id"),
							rs.getInt("chunk_index"),
							rs.getString("content"),
							score
					);
				},
				workspaceId
		);

		return all.stream()
				.filter(c -> c.score() >= 0)
				.sorted(Comparator.comparingDouble(ScoredChunk::score).reversed())
				.limit(topK)
				.toList();
	}

	private static double cosine(float[] a, float[] b) {
		if (a.length != b.length) {
			return -1;
		}
		double dot = 0;
		double na = 0;
		double nb = 0;
		for (int i = 0; i < a.length; i++) {
			dot += a[i] * b[i];
			na += a[i] * a[i];
			nb += b[i] * b[i];
		}
		if (na == 0 || nb == 0) {
			return 0;
		}
		return dot / (Math.sqrt(na) * Math.sqrt(nb));
	}
}
