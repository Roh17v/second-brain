package com.secondbrain.document.ingestion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

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

	private static final Pattern MARKDOWN_HEADING = Pattern.compile("^(#{1,6})\\s+(\\S.*)$");

	private static final Pattern UNIT_HEADING = Pattern.compile("(?i)^Unit[-\\s]?\\d+\\b.*$");

	/** Circled / dingbat step markers: ① Requirement, ② Ticket Server */
	private static final Pattern STEP_HEADING = Pattern.compile(
			"^[\\u2460-\\u2473\\u2776-\\u277F\\u24EB-\\u24F4•]\\s*.*"
	);

	public List<String> chunk(String text) {
		return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
	}

	/**
	 * Prefer heading-based sections; fall back to character windows when the
	 * document has no usable outline.
	 */
	public List<StructuredChunk> chunkDocument(String text) {
		return chunkDocument(text, DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
	}

	public List<StructuredChunk> chunkDocument(String text, int chunkSize, int chunkOverlap) {
		if (text == null) {
			return List.of();
		}
		String normalized = text.replace("\r\n", "\n").trim();
		if (normalized.isEmpty()) {
			return List.of();
		}

		List<HeadingSpan> headings = findMajorHeadings(normalized);
		if (headings.size() < 2) {
			return toUnstructured(chunk(normalized, chunkSize, chunkOverlap));
		}

		List<Section> sections = buildSections(normalized, headings);
		sections = mergeEmptySections(sections);

		List<StructuredChunk> out = new ArrayList<>();
		for (Section section : sections) {
			if (section.body().isBlank() && (section.heading() == null || section.heading().isBlank())) {
				continue;
			}
			String body = section.body().isBlank() ? section.heading() : section.body();
			out.addAll(splitSection(section.heading(), body, chunkSize, chunkOverlap));
		}
		return out;
	}

	private static List<Section> buildSections(String text, List<HeadingSpan> headings) {
		List<Section> sections = new ArrayList<>();
		if (headings.getFirst().start() > 0) {
			String preamble = text.substring(0, headings.getFirst().start()).strip();
			if (!preamble.isEmpty()) {
				sections.add(new Section(null, preamble));
			}
		}
		for (int i = 0; i < headings.size(); i++) {
			HeadingSpan h = headings.get(i);
			int bodyStart = h.start() + h.rawLine().length();
			int bodyEnd = (i + 1 < headings.size()) ? headings.get(i + 1).start() : text.length();
			String body = text.substring(Math.min(bodyStart, Math.max(bodyStart, bodyEnd)), bodyEnd).strip();
			sections.add(new Section(h.title(), body));
		}
		return sections;
	}

	/**
	 * Heading-only slices (title with no real body) fold into the next section
	 * so we do not persist empty "Section: Foo / Foo" chunks.
	 */
	static List<Section> mergeEmptySections(List<Section> sections) {
		if (sections == null || sections.size() < 2) {
			return sections == null ? List.of() : sections;
		}
		List<Section> merged = new ArrayList<>();
		for (int i = 0; i < sections.size(); i++) {
			Section current = sections.get(i);
			if (i < sections.size() - 1 && isHeadingOnly(current)) {
				Section next = sections.get(i + 1);
				String prefix = current.heading() == null ? "" : current.heading();
				String combinedBody = (prefix.isBlank() ? "" : prefix + "\n") + next.body();
				String heading = next.heading() != null ? next.heading() : current.heading();
				sections.set(i + 1, new Section(heading, combinedBody.strip()));
			}
			else {
				merged.add(current);
			}
		}
		return merged;
	}

	static boolean isHeadingOnly(Section section) {
		if (section == null) {
			return false;
		}
		String body = section.body() == null ? "" : section.body().strip();
		if (body.isEmpty()) {
			return true;
		}
		String heading = section.heading() == null ? "" : section.heading().strip();
		return !heading.isEmpty() && body.equalsIgnoreCase(heading);
	}

	private List<StructuredChunk> splitSection(
			String heading,
			String body,
			int chunkSize,
			int chunkOverlap
	) {
		String prefixed = withSectionPrefix(heading, body);
		if (prefixed.length() <= chunkSize) {
			return List.of(new StructuredChunk(prefixed, heading));
		}
		List<StructuredChunk> parts = new ArrayList<>();
		for (String piece : chunk(body, chunkSize, chunkOverlap)) {
			parts.add(new StructuredChunk(withSectionPrefix(heading, piece), heading));
		}
		return parts;
	}

	static String withSectionPrefix(String heading, String body) {
		if (heading == null || heading.isBlank()) {
			return body == null ? "" : body.strip();
		}
		String b = body == null ? "" : body.strip();
		if (b.startsWith("Section: ")) {
			return b;
		}
		return "Section: " + heading.strip() + "\n" + b;
	}

	private static List<StructuredChunk> toUnstructured(List<String> parts) {
		List<StructuredChunk> out = new ArrayList<>(parts.size());
		for (String p : parts) {
			out.add(new StructuredChunk(p, null));
		}
		return out;
	}

	static List<HeadingSpan> findMajorHeadings(String text) {
		List<HeadingSpan> found = new ArrayList<>();
		int offset = 0;
		String[] lines = text.split("\n", -1);
		for (String line : lines) {
			if (isMajorHeadingLine(line)) {
				found.add(new HeadingSpan(offset, line, cleanHeading(line)));
			}
			offset += line.length() + 1; // +1 for the '\n' (last line may not have one)
		}
		return found;
	}

	static boolean isHeadingLine(String line) {
		if (line == null) {
			return false;
		}
		String t = line.strip();
		if (t.length() < 3 || t.length() > 200) {
			return false;
		}
		if (t.startsWith("```") || t.startsWith("$$") || t.equals("---") || t.equals("***")) {
			return false;
		}
		if (MARKDOWN_HEADING.matcher(t).matches()) {
			return true;
		}
		return UNIT_HEADING.matcher(t).matches();
	}

	static boolean isMajorHeadingLine(String line) {
		if (!isHeadingLine(line)) {
			return false;
		}
		int level = headingLevel(line);
		String title = cleanHeading(line);
		return isMajorHeading(title, level);
	}

	/**
	 * Top-level chapter titles become section breaks. Step markers (①, •)
	 * and deeper markdown headings stay inside the parent section.
	 */
	static boolean isMajorHeading(String title, int level) {
		if (title == null || title.isBlank()) {
			return false;
		}
		String t = title.strip();
		if (UNIT_HEADING.matcher(t).matches()) {
			return true;
		}
		if (STEP_HEADING.matcher(t).matches()) {
			return false;
		}
		String lower = t.toLowerCase();
		if (lower.contains("design")
				|| lower.contains("high level")
				|| lower.startsWith("chapter")
				|| lower.matches("part\\s+\\d+.*")) {
			return true;
		}
		if (level >= 2) {
			return false;
		}
		return t.length() >= 20;
	}

	static int headingLevel(String line) {
		String t = line == null ? "" : line.strip();
		var m = MARKDOWN_HEADING.matcher(t);
		if (m.matches()) {
			return m.group(1).length();
		}
		return 1;
	}

	static String cleanHeading(String raw) {
		String t = raw.strip();
		var m = MARKDOWN_HEADING.matcher(t);
		if (m.matches()) {
			t = m.group(2).strip();
		}
		t = t.replaceAll("\\s+", " ");
		if (t.length() > 200) {
			t = t.substring(0, 200);
		}
		return t;
	}

	record HeadingSpan(int start, String rawLine, String title) {
	}

	record Section(String heading, String body) {
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
