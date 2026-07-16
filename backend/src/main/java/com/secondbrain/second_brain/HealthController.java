package com.secondbrain.second_brain;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HealthController {

	private final DataSource dataSource;

	public HealthController(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@GetMapping("/api/health")
	public Map<String, String> health() {
		Map<String, String> body = new LinkedHashMap<>();
		body.put("status", "ok");

		try (var connection = dataSource.getConnection()) {
			body.put("database", connection.isValid(2) ? "up" : "down");
		}
		catch (Exception ex) {
			body.put("database", "down");
		}

		return body;
	}
}
