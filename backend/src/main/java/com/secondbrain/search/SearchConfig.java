package com.secondbrain.search;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.secondbrain.search.rerank.ChunkReranker;
import com.secondbrain.search.rerank.IdentityChunkReranker;
import com.secondbrain.search.rerank.LexicalChunkReranker;

@Configuration
@EnableConfigurationProperties(SearchProperties.class)
public class SearchConfig {

	@Bean
	public ChunkReranker chunkReranker(SearchProperties properties) {
		if (!properties.isRerankEnabled()) {
			return new IdentityChunkReranker();
		}
		String provider = properties.getRerankProvider() == null
				? ""
				: properties.getRerankProvider().strip().toLowerCase();
		if (provider.equals(IdentityChunkReranker.ID) || provider.equals("none")) {
			return new IdentityChunkReranker();
		}
		return new LexicalChunkReranker();
	}
}
