package com.secondbrain.document.ingestion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.secondbrain.ai.ocr.OcrClient;
import com.secondbrain.ai.ocr.OcrProperties;
import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.StorageException;

/**
 * Extracts plain text from stored document files (pdf / txt / md / images).
 * PDFs use PDFBox first; sparse/empty text falls back to Mistral OCR when enabled.
 */
@Component
public class TextExtractor {

	private static final Logger log = LoggerFactory.getLogger(TextExtractor.class);

	private static final Set<String> IMAGE_EXTENSIONS = Set.of(
			"png", "jpg", "jpeg", "webp", "gif", "avif"
	);

	private final OcrClient ocrClient;
	private final OcrProperties ocrProperties;

	public TextExtractor(OcrClient ocrClient, OcrProperties ocrProperties) {
		this.ocrClient = ocrClient;
		this.ocrProperties = ocrProperties;
	}

	public String extract(String filename, InputStream inputStream) {
		String lower = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
		try {
			if (lower.endsWith(".pdf")) {
				return extractPdf(filename, inputStream);
			}
			if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
				return extractPlainText(inputStream);
			}
			String ext = extensionOf(lower);
			if (IMAGE_EXTENSIONS.contains(ext)) {
				return extractImage(filename, ext, inputStream);
			}
			throw new BadRequestException("Unsupported file type for text extraction");
		}
		catch (IOException ex) {
			throw new StorageException("Failed to extract text from: " + filename, ex);
		}
	}

	private static String extractPlainText(InputStream inputStream) throws IOException {
		byte[] bytes = inputStream.readAllBytes();
		return new String(bytes, StandardCharsets.UTF_8);
	}

	private String extractPdf(String filename, InputStream inputStream) throws IOException {
		// PDFBox needs random access; buffer fully into memory for MVP-sized files (<= 20MB).
		byte[] bytes = new BufferedInputStream(inputStream).readAllBytes();

		String digitalText;
		try (PDDocument document = Loader.loadPDF(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			digitalText = stripper.getText(document);
		}

		if (hasEnoughText(digitalText)) {
			log.debug("PDFBox extracted {} chars from {}", significantLength(digitalText), filename);
			return digitalText;
		}

		if (!ocrClient.isEnabled()) {
			if (digitalText == null || digitalText.isBlank()) {
				throw new BadRequestException(
						"No extractable text found in PDF (likely a scan/image PDF). "
								+ "Enable OCR: set OCR_PROVIDER=mistral and MISTRAL_API_KEY."
				);
			}
			// Sparse but non-empty digital text — still return it rather than fail hard.
			log.warn(
					"Sparse digital text ({} chars) in {} and OCR disabled; using PDFBox output",
					significantLength(digitalText),
					filename
			);
			return digitalText;
		}

		log.info(
				"Sparse/empty PDF text ({} chars) for {}; falling back to Mistral OCR",
				significantLength(digitalText),
				filename
		);
		return ocrClient.ocrPdf(bytes, filename);
	}

	private String extractImage(String filename, String ext, InputStream inputStream) throws IOException {
		if (!ocrClient.isEnabled()) {
			throw new BadRequestException(
					"Image OCR requires OCR_PROVIDER=mistral and MISTRAL_API_KEY."
			);
		}
		byte[] bytes = inputStream.readAllBytes();
		String mime = switch (ext) {
			case "jpg", "jpeg" -> "image/jpeg";
			case "png" -> "image/png";
			case "webp" -> "image/webp";
			case "gif" -> "image/gif";
			case "avif" -> "image/avif";
			default -> "image/png";
		};
		return ocrClient.ocrImage(bytes, mime, filename);
	}

	private boolean hasEnoughText(String text) {
		return significantLength(text) >= ocrProperties.getMinTextChars();
	}

	private static int significantLength(String text) {
		if (text == null) {
			return 0;
		}
		int n = 0;
		for (int i = 0; i < text.length(); i++) {
			if (!Character.isWhitespace(text.charAt(i))) {
				n++;
			}
		}
		return n;
	}

	private static String extensionOf(String lowerFilename) {
		int dot = lowerFilename.lastIndexOf('.');
		if (dot < 0 || dot == lowerFilename.length() - 1) {
			return "";
		}
		return lowerFilename.substring(dot + 1);
	}
}
