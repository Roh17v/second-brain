package com.secondbrain.ai.ocr;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.secondbrain.common.exception.BadRequestException;

/**
 * OCR disabled ({@code app.ocr.provider=none}).
 */
@Component
@ConditionalOnProperty(name = "app.ocr.provider", havingValue = com.secondbrain.ai.AiProviders.OCR_NONE, matchIfMissing = true)
public class NoopOcrClient implements OcrClient {

	@Override
	public boolean isEnabled() {
		return false;
	}

	@Override
	public String ocrPdf(byte[] pdfBytes, String filename) {
		throw new BadRequestException(
				"OCR is disabled. Set OCR_PROVIDER=mistral and MISTRAL_API_KEY to process scanned PDFs."
		);
	}

	@Override
	public String ocrImage(byte[] imageBytes, String mimeType, String filename) {
		throw new BadRequestException(
				"OCR is disabled. Set OCR_PROVIDER=mistral and MISTRAL_API_KEY to process images."
		);
	}
}
