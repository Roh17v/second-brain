package com.secondbrain.ai.ocr;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.secondbrain.common.exception.BadRequestException;

/**
 * Mistral Document OCR ({@code POST /v1/ocr}).
 *
 * @see <a href="https://docs.mistral.ai/studio-api/document-processing/basic_ocr">Basic OCR</a>
 */
@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = com.secondbrain.ai.AiProviders.OCR_MISTRAL)
public class MistralOcrClient implements OcrClient {

	private static final Logger log = LoggerFactory.getLogger(MistralOcrClient.class);

	private final OcrProperties properties;
	private final RestClient restClient;
	private final ObjectMapper objectMapper;

	public MistralOcrClient(OcrProperties properties, ObjectMapper objectMapper) {
		this.properties = properties;
		this.objectMapper = objectMapper;

		var requestFactory = new JdkClientHttpRequestFactory();
		requestFactory.setReadTimeout(java.time.Duration.ofSeconds(Math.max(30, properties.getTimeoutSeconds())));

		this.restClient = RestClient.builder()
				.baseUrl(trimTrailingSlash(properties.getBaseUrl()))
				.requestFactory(requestFactory)
				.build();
	}

	@Override
	public boolean isEnabled() {
		return true;
	}

	@Override
	public String ocrPdf(byte[] pdfBytes, String filename) {
		if (pdfBytes == null || pdfBytes.length == 0) {
			throw new BadRequestException("Cannot OCR an empty PDF");
		}
		String dataUrl = "data:application/pdf;base64," + Base64.getEncoder().encodeToString(pdfBytes);
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("type", "document_url");
		document.put("document_url", dataUrl);
		return process(document, filename);
	}

	@Override
	public String ocrImage(byte[] imageBytes, String mimeType, String filename) {
		if (imageBytes == null || imageBytes.length == 0) {
			throw new BadRequestException("Cannot OCR an empty image");
		}
		String mime = (mimeType == null || mimeType.isBlank()) ? "image/png" : mimeType;
		String dataUrl = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(imageBytes);
		Map<String, Object> document = new LinkedHashMap<>();
		document.put("type", "image_url");
		document.put("image_url", dataUrl);
		return process(document, filename);
	}

	private String process(Map<String, Object> document, String filename) {
		requireApiKey();

		Map<String, Object> body = new LinkedHashMap<>();
		body.put("model", properties.getModel());
		body.put("document", document);
		// Keep tables inline as markdown in page.markdown (default null)
		body.put("include_image_base64", false);

		try {
			String raw = restClient.post()
					.uri("/v1/ocr")
					.contentType(MediaType.APPLICATION_JSON)
					.header("Authorization", "Bearer " + properties.getApiKey().trim())
					.body(body)
					.retrieve()
					.body(String.class);

			String markdown = joinPages(raw);
			if (markdown == null || markdown.isBlank()) {
				throw new BadRequestException("Mistral OCR returned no text for: " + filename);
			}
			log.info("Mistral OCR extracted {} characters from {}", markdown.length(), filename);
			return markdown;
		}
		catch (RestClientResponseException ex) {
			String detail = safeBody(ex.getResponseBodyAsString());
			throw new BadRequestException(
					"Mistral OCR failed (" + ex.getStatusCode().value() + ") for "
							+ filename + ": " + detail
			);
		}
		catch (RestClientException ex) {
			throw new BadRequestException(
					"Failed to call Mistral OCR at " + properties.getBaseUrl()
							+ ". Check network and MISTRAL_API_KEY. (" + ex.getMessage() + ")"
			);
		}
		catch (BadRequestException ex) {
			throw ex;
		}
		catch (Exception ex) {
			throw new BadRequestException("Failed to parse Mistral OCR response: " + ex.getMessage());
		}
	}

	private String joinPages(String rawJson) throws Exception {
		JsonNode root = objectMapper.readTree(rawJson);
		JsonNode pages = root.path("pages");
		if (!pages.isArray() || pages.isEmpty()) {
			return "";
		}
		List<String> parts = new ArrayList<>();
		for (JsonNode page : pages) {
			String md = page.path("markdown").asText("").trim();
			if (!md.isEmpty()) {
				parts.add(md);
			}
		}
		return String.join("\n\n", parts).trim();
	}

	private void requireApiKey() {
		if (!properties.hasApiKey()) {
			throw new BadRequestException(
					"Mistral OCR is enabled but MISTRAL_API_KEY is not set. "
							+ "Add the key to your environment (do not commit it)."
			);
		}
	}

	private static String trimTrailingSlash(String url) {
		if (url == null || url.isBlank()) {
			return "https://api.mistral.ai";
		}
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	private static String safeBody(String body) {
		if (body == null || body.isBlank()) {
			return "(empty response body)";
		}
		String trimmed = body.replaceAll("\\s+", " ").trim();
		return trimmed.length() > 400 ? trimmed.substring(0, 400) + "…" : trimmed;
	}
}
