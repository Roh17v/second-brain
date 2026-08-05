package com.secondbrain.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableAsync
public class AsyncConfig {

	/**
	 * Background workers for document process + embed after upload.
	 * Small pool fits a 2 vCPU VPS without drowning the host.
	 */
	@Bean(name = "documentIngestionExecutor")
	Executor documentIngestionExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("doc-ingest-");
		executor.initialize();
		return executor;
	}
}
