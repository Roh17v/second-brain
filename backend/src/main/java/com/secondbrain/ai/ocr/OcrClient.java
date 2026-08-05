package com.secondbrain.ai.ocr;

/**
 * Document OCR — extract text/markdown from image-only PDFs and images.
 */
public interface OcrClient {

	boolean isEnabled();

	/**
	 * OCR a PDF file (bytes). Returns markdown text for the whole document.
	 */
	String ocrPdf(byte[] pdfBytes, String filename);

	/**
	 * OCR a single image (png/jpeg/webp/…). Returns markdown text.
	 */
	String ocrImage(byte[] imageBytes, String mimeType, String filename);
}
