package com.secondbrain.document.ingestion;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import com.secondbrain.common.exception.BadRequestException;
import com.secondbrain.common.exception.StorageException;

/**
 * Extracts plain text from stored document files (pdf / txt / md).
 */
@Component
public class TextExtractor {

	public String extract(String filename, InputStream inputStream) {
		String lower = filename.toLowerCase(Locale.ROOT);
		try {
			if (lower.endsWith(".pdf")) {
				return extractPdf(inputStream);
			}
			if (lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")) {
				return extractPlainText(inputStream);
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

	private static String extractPdf(InputStream inputStream) throws IOException {
		// PDFBox needs random access; buffer fully into memory for MVP-sized files (<= 20MB).
		byte[] bytes = new BufferedInputStream(inputStream).readAllBytes();
		try (PDDocument document = Loader.loadPDF(bytes)) {
			PDFTextStripper stripper = new PDFTextStripper();
			return stripper.getText(document);
		}
	}
}
