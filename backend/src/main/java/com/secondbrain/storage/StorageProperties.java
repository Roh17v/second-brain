package com.secondbrain.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

	/**
	 * Root directory for uploaded files (relative or absolute).
	 */
	private String basePath = "./storage";

	public String getBasePath() {
		return basePath;
	}

	public void setBasePath(String basePath) {
		this.basePath = basePath;
	}
}
