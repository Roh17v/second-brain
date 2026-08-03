package com.secondbrain.document.ingestion;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Recursive character text splitter inspired by common RAG defaults
 * (e.g. Adaptive-Rag: chunk_size=1000, chunk_overlap=150).
 */
@Component
public class TextChunker {

	public static final int DEFAULT_CHUNK_SIZE = 1000;
	public static final int DEFAULT_CHUNK_OVERLAP = 150;

	private static final List<String> SEPARATORS = List.of("\n\n", "\n", ". ", " ", "");

	public List<String> chunk(String text) {
		return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
	}

	public List<String> chunk(String text, int chunkSize, int chunkOverlap) {
		if (text == null) {
			return List.of();
		}
		String normalized = text.replace("\r\n", "\n").trim();
		if (normalized.isEmpty()) {
			return List.of();
		}
		if (chunkOverlap >= chunkSize) {
			throw new IllegalArgumentException("chunkOverlap must be smaller than chunkSize");
		}
		return splitRecursive(normalized, chunkSize, chunkOverlap, SEPARATORS);
	}

	private List<String> splitRecursive(
			String text,
			int chunkSize,
			int chunkOverlap,
			List<String> separators
	) {
		List<String> finalChunks = new ArrayList<>();
		if (text.length() <= chunkSize) {
			if (!text.isBlank()) {
				finalChunks.add(text.trim());
			}
			return finalChunks;
		}

		String separator = separators.get(separators.size() - 1);
		List<String> nextSeparators = List.of();
		for (int i = 0; i < separators.size(); i++) {
			String candidate = separators.get(i);
			if (candidate.isEmpty() || text.contains(candidate)) {
				separator = candidate;
				nextSeparators = separators.subList(i + 1, separators.size());
				break;
			}
		}

		List<String> splits = separator.isEmpty()
				? splitBySize(text, chunkSize)
				: splitKeepDelimiterLogic(text, separator);

		List<String> merged = mergeSplits(splits, chunkSize, chunkOverlap);
		for (String part : merged) {
			if (part.length() <= chunkSize) {
				if (!part.isBlank()) {
					finalChunks.add(part.trim());
				}
			}
			else if (!nextSeparators.isEmpty()) {
				finalChunks.addAll(splitRecursive(part, chunkSize, chunkOverlap, nextSeparators));
			}
			else {
				// Hard split as last resort
				for (int i = 0; i < part.length(); i += chunkSize - chunkOverlap) {
					int end = Math.min(part.length(), i + chunkSize);
					String slice = part.substring(i, end).trim();
					if (!slice.isEmpty()) {
						finalChunks.add(slice);
					}
					if (end == part.length()) {
						break;
					}
				}
			}
		}
		return finalChunks;
	}

	private static List<String> splitBySize(String text, int size) {
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < text.length(); i += size) {
			parts.add(text.substring(i, Math.min(text.length(), i + size)));
		}
		return parts;
	}

	private static List<String> splitKeepDelimiterLogic(String text, String separator) {
		String[] raw = text.split(java.util.regex.Pattern.quote(separator), -1);
		List<String> parts = new ArrayList<>();
		for (int i = 0; i < raw.length; i++) {
			String piece = raw[i];
			if (i < raw.length - 1) {
				piece = piece + separator;
			}
			if (!piece.isBlank()) {
				parts.add(piece);
			}
		}
		return parts;
	}

	private static List<String> mergeSplits(List<String> splits, int chunkSize, int chunkOverlap) {
		List<String> chunks = new ArrayList<>();
		StringBuilder current = new StringBuilder();

		for (String split : splits) {
			if (current.length() + split.length() <= chunkSize) {
				current.append(split);
			}
			else {
				if (!current.isEmpty()) {
					chunks.add(current.toString());
					String overlap = tailOverlap(current.toString(), chunkOverlap);
					current = new StringBuilder(overlap);
				}
				if (split.length() > chunkSize) {
					if (!current.isEmpty()) {
						chunks.add(current.toString());
						current = new StringBuilder();
					}
					chunks.add(split);
				}
				else {
					current.append(split);
				}
			}
		}
		if (!current.isEmpty()) {
			chunks.add(current.toString());
		}
		return chunks;
	}

	private static String tailOverlap(String text, int overlap) {
		if (overlap <= 0 || text.isEmpty()) {
			return "";
		}
		if (text.length() <= overlap) {
			return text;
		}
		return text.substring(text.length() - overlap);
	}
}
