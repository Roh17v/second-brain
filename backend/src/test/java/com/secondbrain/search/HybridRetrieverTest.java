package com.secondbrain.search;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.secondbrain.ai.embedding.EmbeddingClient;
import com.secondbrain.search.rerank.IdentityChunkReranker;
import com.secondbrain.search.rerank.LexicalChunkReranker;

class HybridRetrieverTest {

	private static final UUID WORKSPACE = UUID.randomUUID();
	private static final UUID DOC = UUID.randomUUID();
	private static final UUID HEAP = UUID.randomUUID();
	private static final UUID STACK = UUID.randomUUID();
	private static final UUID OTHER = UUID.randomUUID();

	@Test
	void hybridLiftsKeywordHitThatDenseRanksLow() {
		ScoredChunk heap = chunk(HEAP, 0, "Heapify O(n log n) occupies several pages.", 0.92);
		ScoredChunk other = chunk(OTHER, 1, "Priority queue introduction.", 0.80);
		ScoredChunk stack = chunk(STACK, 2, "Stack push and pop are O(1).", 0.41);

		FakeStore store = new FakeStore(
				List.of(heap, other, stack),
				List.of(stack)
		);
		SearchProperties props = new SearchProperties();
		props.setHybridEnabled(true);
		props.setCandidateK(20);

		HybridRetriever retriever = retriever(store, props, true);
		List<SearchHitResponse> hits = retriever.retrieve(
				WORKSPACE,
				List.of("time complexity stack O(1)"),
				5
		);

		assertEquals(3, hits.size());
		assertEquals(STACK, hits.getFirst().chunkId());
		assertEquals(1, store.keywordCalls);
		assertEquals(1, store.denseCalls);
	}

	@Test
	void hybridDisabledUsesDenseOnlyAndPreservesOrder() {
		ScoredChunk heap = chunk(HEAP, 0, "heap", 0.9);
		ScoredChunk stack = chunk(STACK, 1, "stack O(1)", 0.4);

		FakeStore store = new FakeStore(List.of(heap, stack), List.of(stack));
		SearchProperties props = new SearchProperties();
		props.setHybridEnabled(false);
		props.setRerankEnabled(false);

		HybridRetriever retriever = retriever(store, props, false);
		List<SearchHitResponse> hits = retriever.retrieve(WORKSPACE, List.of("stack complexity"), 5);

		assertEquals(HEAP, hits.getFirst().chunkId());
		assertEquals(0, store.keywordCalls);
		assertEquals(1, store.denseCalls);
		assertEquals(0.9, hits.getFirst().score());
	}

	@Test
	void multiQueryRunsBothChannelsPerQuery() {
		FakeStore store = new FakeStore(
				List.of(chunk(HEAP, 0, "heap", 0.5)),
				List.of()
		);
		SearchProperties props = new SearchProperties();
		props.setHybridEnabled(true);

		HybridRetriever retriever = retriever(store, props, true);
		retriever.retrieve(WORKSPACE, List.of("heap complexity", "stack complexity"), 5);

		assertEquals(2, store.denseCalls);
		assertEquals(2, store.keywordCalls);
	}

	@Test
	void rerankLiftsBigOFactWhenIntroWinsBothChannels() {
		ScoredChunk intro = chunk(OTHER, 1, "stacks queues trees and space complexity requirements", 0.95);
		ScoredChunk stack = chunk(STACK, 83, "Advantages of Stack O(1) time complexity for push and pop", 0.40);

		FakeStore store = new FakeStore(
				List.of(intro, stack),
				List.of(intro, stack)
		);
		SearchProperties props = new SearchProperties();
		props.setHybridEnabled(true);

		HybridRetriever retriever = retriever(store, props, true);
		List<SearchHitResponse> hits = retriever.retrieve(
				WORKSPACE,
				List.of("time complexity Stack"),
				5
		);

		assertEquals(STACK, hits.getFirst().chunkId());
	}

	@Test
	void emptyQueriesReturnEmpty() {
		FakeStore store = new FakeStore(List.of(), List.of());
		HybridRetriever retriever = retriever(store, new SearchProperties(), true);
		assertTrue(retriever.retrieve(WORKSPACE, List.of("  ", ""), 5).isEmpty());
		assertEquals(0, store.denseCalls);
	}

	@Test
	void candidateLimitWidensFirstStage() {
		SearchProperties props = new SearchProperties();
		props.setCandidateK(20);
		props.setMaxCandidateK(50);
		assertEquals(20, props.candidateLimit(5));
		assertEquals(50, props.candidateLimit(50));
		assertEquals(30, props.candidateLimit(30));
	}

	@Test
	void finalTopKUsesRerankFloor() {
		SearchProperties props = new SearchProperties();
		props.setRerankEnabled(true);
		props.setRerankTopK(8);
		assertEquals(8, props.finalTopK(5, 1));
		assertEquals(8, props.finalTopK(8, 1));
		assertEquals(10, props.finalTopK(5, 10));
	}

	private static HybridRetriever retriever(FakeStore store, SearchProperties props, boolean lexical) {
		return new HybridRetriever(
				new DummyEmbed(),
				store,
				props,
				lexical ? new LexicalChunkReranker() : new IdentityChunkReranker()
		);
	}

	private static ScoredChunk chunk(UUID id, int index, String content, double score) {
		return new ScoredChunk(id, DOC, index, content, score);
	}

	private static final class DummyEmbed implements EmbeddingClient {
		@Override
		public float[] embed(String text) {
			return new float[] { 1f, 0f };
		}

		@Override
		public int dimensions() {
			return 2;
		}

		@Override
		public String modelId() {
			return "dummy";
		}
	}

	private static final class FakeStore implements VectorStore {
		private final List<ScoredChunk> dense;
		private final List<ScoredChunk> sparse;
		private int denseCalls;
		private int keywordCalls;

		private FakeStore(List<ScoredChunk> dense, List<ScoredChunk> sparse) {
			this.dense = dense;
			this.sparse = sparse;
		}

		@Override
		public void ensureSchema(int dimensions) {
		}

		@Override
		public void saveEmbedding(UUID chunkId, float[] embedding, String modelId) {
		}

		@Override
		public boolean hasEmbedding(UUID chunkId) {
			return false;
		}

		@Override
		public long countEmbeddedChunks(UUID documentId) {
			return 0;
		}

		@Override
		public List<ScoredChunk> similaritySearch(UUID workspaceId, float[] queryEmbedding, int topK) {
			denseCalls++;
			return take(dense, topK);
		}

		@Override
		public List<ScoredChunk> keywordSearch(UUID workspaceId, String query, int topK) {
			keywordCalls++;
			return take(sparse, topK);
		}

		private static List<ScoredChunk> take(List<ScoredChunk> src, int topK) {
			if (src.size() <= topK) {
				return src;
			}
			return new ArrayList<>(src.subList(0, topK));
		}
	}
}
