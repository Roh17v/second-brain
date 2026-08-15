package com.secondbrain.search;

import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.secondbrain.ai.embedding.EmbeddingProperties;

/**
 * Process-wide embed latency. Starts from config (VPS nomic ≈ 4500 ms) and
 * eases toward measured chunk times so the next document is closer to reality.
 */
@Component
public class EmbedSpeedTracker {

	private final AtomicLong avgMs;

	public EmbedSpeedTracker(EmbeddingProperties properties) {
		this.avgMs = new AtomicLong(Math.max(1L, properties.getEstimatedMsPerChunk()));
	}

	public long averageMs() {
		return avgMs.get();
	}

	public void record(long elapsedMs) {
		if (elapsedMs <= 0) {
			return;
		}
		avgMs.updateAndGet(prev -> Math.round(prev * 0.7 + elapsedMs * 0.3));
	}
}
