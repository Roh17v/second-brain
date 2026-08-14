package com.secondbrain.document.ingestion;

/**
 * One persistable / embeddable piece after ingest splitting.
 *
 * @param content         text stored and embedded (includes {@code Section: …} when structured)
 * @param sectionHeading  heading this piece belongs to, or {@code null}
 */
public record StructuredChunk(String content, String sectionHeading) {
}
