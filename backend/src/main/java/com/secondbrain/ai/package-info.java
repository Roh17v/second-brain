/**
 * Pluggable AI providers for SecondBrain.
 *
 * <h2>Design rule</h2>
 * Business code (chat, search, ingestion) depends only on interfaces:
 * <ul>
 *   <li>{@link com.secondbrain.ai.embedding.EmbeddingClient}</li>
 *   <li>{@link com.secondbrain.ai.llm.LlmClient}</li>
 *   <li>{@link com.secondbrain.ai.ocr.OcrClient}</li>
 * </ul>
 * Never import a concrete provider (Ollama, Gemini, Mistral, …) from those packages.
 *
 * <h2>How to swap providers</h2>
 * Change environment / config only — no chat or search code changes:
 * <pre>
 *   EMBEDDING_PROVIDER=ollama|gemini|hashing
 *   LLM_PROVIDER=ollama|gemini|echo
 *   OCR_PROVIDER=none|mistral
 *   + model, base URL, API key, dimensions as needed
 * </pre>
 *
 * <h2>How to add a provider</h2>
 * <ol>
 *   <li>Implement the interface in {@code ai.embedding}, {@code ai.llm}, or {@code ai.ocr}.</li>
 *   <li>Annotate with {@code @Component} +
 *       {@code @ConditionalOnProperty(name = "app.*.provider", havingValue = "…")}.</li>
 *   <li>Read settings from the shared {@code *Properties} bean (apiKey, baseUrl, model, …).</li>
 *   <li>Document env vars in {@code application.yml} and {@code .env.example}.</li>
 * </ol>
 *
 * <h2>Embeddings caveat</h2>
 * Vectors from different models (or dimensions) are incompatible.
 * Changing {@code app.embedding.model} / dimensions requires re-embedding all documents.
 */
package com.secondbrain.ai;
