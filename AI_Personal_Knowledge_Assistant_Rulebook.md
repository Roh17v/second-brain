# AI Personal Knowledge Assistant --- Engineering Rulebook

> Version: 0.4 (Living Document)

## Purpose

This document defines the engineering principles, architecture, and
implementation rules for the project. Every contributor (human or AI
agent) should follow these rules unless there is a documented
architectural decision changing them.

------------------------------------------------------------------------

# Product Vision

Build a private AI-powered personal knowledge assistant that allows
users to upload documents, search their knowledge semantically, and chat
with their knowledge using Retrieval-Augmented Generation (RAG).

Core principles:

-   Privacy first
-   Source-grounded answers
-   Modular architecture
-   Production-quality engineering
-   AI should augment knowledge, not invent it
-   Knowledge representation is separate from AI inference (portable,
    human-readable knowledge independent of any specific LLM)

------------------------------------------------------------------------

# Functional Requirements

-   User registration and login
-   JWT-based authentication
-   Workspace management
-   Upload PDF, TXT and Markdown files
-   Parse documents into text
-   Chunk documents
-   Generate embeddings
-   Store embeddings using PostgreSQL + pgvector
-   Semantic retrieval
-   Chat using Ollama + Qwen 3 4B
-   Conversation history
-   Document management
-   Soft delete

------------------------------------------------------------------------

# Non-Functional Requirements

-   Modular design
-   Clear separation of concerns
-   Security by default
-   Maintainable code
-   Extensible AI provider abstraction
-   Production-ready logging
-   Scalable architecture
-   UUIDs for primary entities

------------------------------------------------------------------------

# Architecture

Frontend - React + TypeScript (Vite SPA under `apps/web`)

Backend - Spring Boot (`backend/`)

Database - PostgreSQL - pgvector

LLM - Ollama - Qwen 3 4B

Storage - Local storage initially

Monorepo layout:

```text
apps/web      — web client
apps/mobile   — future mobile client
backend/      — Spring Boot API
```

------------------------------------------------------------------------

# Architectural Principles

1.  Controllers are thin.
2.  Business logic belongs in the application/service layer.
3.  Infrastructure details must be abstracted behind interfaces.
4.  Business logic must not depend directly on Ollama, PostgreSQL, or
    local storage.
5.  Prefer dependency inversion.
6.  Design modules so providers can be replaced with minimal changes.
7.  **Separate knowledge representation from AI inference.** Knowledge
    should remain portable, human-readable, and independent of the
    specific LLM used to answer questions. Changing the consumer (Qwen,
    Gemini, OpenAI, Anthropic, etc.) must not require rewriting the
    knowledge itself.

------------------------------------------------------------------------

# Project Structure

Use **feature-based packaging** (package-by-feature), not a global
`controller/` / `service/` / `repository/` split across the whole app.

Shared:

-   `config/` --- Spring configuration
-   `common/` --- exceptions, shared response types, utilities
-   `security/` --- JWT filters and security infrastructure

Features (each self-contained):

-   `auth/` --- register, login, token issuance
-   `user/` --- user profile and user domain
-   `workspace/` --- workspaces
-   `document/` --- uploads and document metadata
-   `chat/` --- conversations and messages
-   `embedding/` --- embedding generation and storage hooks
-   `retrieval/` --- semantic / hybrid retrieval
-   `llm/` --- LLM provider abstraction
-   `storage/` --- file storage abstraction
-   `knowledge/` --- future knowledge-layer module

Within a feature, use layers as needed:

```text
user/
  controller/
  service/
  repository/
  entity/
  dto/
  mapper/
```

Keep controllers thin. Business logic stays in services. Do not expose
entities as API responses; map through DTOs.

------------------------------------------------------------------------

# Database Rules

Entities:

-   User
-   Workspace
-   Document
-   Chunk
-   Conversation
-   Message

Rules:

-   Every entity belongs to a user directly or indirectly.
-   Every document belongs to one workspace.
-   Every chunk belongs to one document.
-   Store embeddings with chunks (Version 1).
-   Use UUID primary keys.
-   Prefer soft delete.

------------------------------------------------------------------------

# AI Rules

-   Never send an entire document to the LLM.
-   Always retrieve relevant chunks first.
-   Use RAG for document answers.
-   Return citations whenever possible.
-   If knowledge is missing, state that instead of hallucinating.

------------------------------------------------------------------------

# Security Rules

-   Every endpoint requires authorization unless explicitly public.
-   Filter all user-owned resources by authenticated user.
-   Never expose another user's data.
-   Validate uploads.
-   Sanitize filenames.

------------------------------------------------------------------------

# Configuration Rules

-   Never commit secrets to Git.
-   All sensitive configuration must come from environment variables.
-   Keep `application.yml` free of environment-specific secrets and
    host-specific values that change between environments.
-   Provide a `.env.example` with placeholders for required variables.
-   Support multiple Spring profiles (`dev`, `prod`) from the beginning.
-   Avoid hardcoded URLs, ports, credentials, and API keys in committed
    source.
-   Typical variables (expand as the system grows): `DB_URL` (JDBC URL;
    may embed user and password), `JWT_SECRET`, `OLLAMA_BASE_URL`,
    `FILE_STORAGE_PATH`, `SERVER_PORT`.
-   Pattern: **everything that changes between environments comes from
    environment variables.**

------------------------------------------------------------------------

# Code Quality Standards

-   Small focused classes.
-   Single Responsibility Principle.
-   Constructor injection.
-   No business logic inside controllers.
-   Avoid duplicated code.
-   Meaningful naming.
-   Centralized exception handling.
-   Consistent DTO usage.

------------------------------------------------------------------------

# Performance Guidelines

-   Do not regenerate embeddings unnecessarily.
-   Cache metadata where appropriate.
-   Avoid unnecessary database queries.
-   Keep retrieval limited to top relevant chunks.

------------------------------------------------------------------------

# Engineering Workflow

Before implementing a feature:

1.  Confirm requirements.
2.  Confirm architecture impact.
3.  Design database changes.
4.  Design API.
5.  Implement.
6.  Test.
7.  Review.

------------------------------------------------------------------------

# Commit Message Convention

This is an industry-standard convention used by many open-source and
production projects. Every commit message must follow this format.

Format:

```text
<type>(optional-scope): <short description>
```

Example:

```text
feat(auth): add JWT authentication
```

## Commit types

### New feature

```text
feat(chat): implement conversation history
feat(document): add PDF upload endpoint
```

### Bug fix

```text
fix(search): return correct similarity score
```

### Refactoring

```text
refactor(ai): extract LLM provider interface
```

### Performance

```text
perf(vector): optimize similarity search query
```

### Documentation

```text
docs(rulebook): add authentication standards
```

### Tests

```text
test(chat): add integration tests for chat service
```

### Chore

```text
chore: configure Spotless formatter
```

### Build

```text
build: configure Docker image
```

### CI

```text
ci: add GitHub Actions workflow
```

## Rules

-   Use a short, imperative description (e.g. "add", not "added" or "adds").
-   Scope is optional but preferred when the change is feature-specific
    (auth, chat, document, search, ai, etc.).
-   One logical change per commit when practical.
-   AI agents and human contributors must follow this format for all
    commits in this repository.

------------------------------------------------------------------------

# Current Technology Decisions

Frontend: - React - TypeScript

Backend: - Java 21 - Spring Boot

Database: - PostgreSQL - pgvector

Local LLM: - Ollama - Qwen 3 4B

Storage: - Local filesystem

Authentication: - JWT

------------------------------------------------------------------------

# Engineering Maturity Roadmap

Do not implement every advanced RAG capability in Version 1. Build in
stages while keeping the architecture flexible enough to support later
phases with minimal redesign.

Every feature should be tagged as one of:

-   **MVP** --- Required for Version 1 (core product scope)
-   **Production** --- Planned after the MVP is stable
-   **Enterprise** --- Advanced capabilities for large-scale operation

Rules:

-   Prefer a complete, working Version 1 over early pursuit of advanced
    retrieval and platform features.
-   Design interfaces and module boundaries so Production and Enterprise
    features can be added cleanly later.
-   When proposing work, state the maturity tag (MVP / Production /
    Enterprise) and which phase it belongs to.
-   Use precise engineering language in docs and commits. Avoid marketing
    or portfolio-style phrasing (e.g. "resume ready").

------------------------------------------------------------------------

## Maturity Levels

### Level 1 --- MVP (Version 1)

Stack:

```text
React → Spring Boot → PostgreSQL + pgvector → Ollama → Qwen 3 → RAG
```

MVP features:

-   Authentication (JWT)
-   Workspaces
-   PDF / TXT / Markdown upload
-   Parsing, chunking, embeddings
-   Vector search (semantic retrieval)
-   Prompt builder
-   Conversation history
-   Source citations
-   Logging
-   Docker

This is Version 1 scope. Implement it with clean architecture and solid
engineering practices.

### Level 2 --- Production

-   Hybrid search (BM25 + vectors + fusion)
-   Cross-encoder reranking
-   Background processing for ingestion / embeddings
-   Streaming responses
-   Caching
-   Retry logic
-   Better monitoring
-   Knowledge article generation (Markdown + metadata from sources)

### Level 3 --- Enterprise

-   Golden dataset evaluation and quality metrics
-   CI/CD quality gates
-   Observability and request tracing
-   Prompt versioning
-   Embedding model versioning
-   Multi-LLM support (beyond Ollama)
-   Cost and latency dashboards
-   Analytics (chats, failures, token usage, etc.)
-   OKF-style knowledge import/export and workspace portability
-   Knowledge graph over structured articles

------------------------------------------------------------------------

## Phased Delivery Roadmap

### Phase 1 --- Core foundation (MVP)

Build the end-to-end RAG loop with auth, workspaces, documents, vector
retrieval, chat, citations, logging, and Docker. No hybrid search or
reranking required in this phase.

### Phase 2 --- Better Retrieval (Production)

Move from pure vector top-K to **hybrid search**:

```text
Query → BM25 → Vector Search → Fusion → Top Results
```

Why hybrid search:

-   Exact identifiers (e.g. `JWT_REFRESH_TOKEN`) favor keyword / BM25.
-   Conceptual questions (e.g. "How does auth flow work?") favor vectors.
-   Combining both improves robustness across query types.

### Phase 3 --- Reranking (Production)

Improve precision after candidate retrieval:

```text
Vector / Hybrid Search → Top N (e.g. 30) → Cross Encoder → Best K (e.g. 5) → LLM
```

Cross-encoders are slower than bi-encoder vector search but score
query and chunk together for higher precision.

### Phase 4 --- Evaluation (Enterprise)

Maintain a golden dataset, for example:

| Question         | Expected Answer | Expected Sources |
| ---------------- | --------------- | ---------------- |
| What is JWT?     | ...             | auth.pdf         |
| Explain sharding | ...             | database.pdf     |

Evaluate major changes automatically. Metrics may include:

-   Answer correctness
-   Grounding (faithfulness)
-   Citation quality
-   Retrieval recall
-   Response latency

### Phase 5 --- Observability (Enterprise)

Treat every request as a traceable pipeline, not a black box:

```text
User Question
  → Embedding time
  → Vector / hybrid search time
  → Reranker time
  → LLM time
  → Total response time
```

When something is slow, the stage responsible should be obvious.

### Phase 6 --- Analytics (Enterprise)

Operational metrics such as:

-   Total chats
-   Average response time
-   Average retrieval latency
-   Average chunks retrieved
-   Failed generations
-   Token usage (if applicable)
-   LLM response time

------------------------------------------------------------------------

## Cross-cutting Production Concerns

These should influence architecture early even if full implementation is
later.

### 1. Ingestion pipeline (MVP path; harden in Production)

Documents become searchable only after a defined pipeline succeeds:

```text
Upload → (Virus scan future) → Parse → Clean → Chunk
  → Generate embeddings → Store → READY
```

Support retries and clear failure states rather than silent partial
ingestion.

### 2. Background jobs (Production; design for in MVP)

Embedding generation must not block the upload HTTP request long-term:

```text
Upload → Queue → Worker → Embeddings → READY
```

MVP may use a simple async approach; Production should use a proper
queue/worker model.

### 3. Versioned embeddings (Enterprise; schema-friendly in MVP)

Track which embedding model produced each vector so model switches do
not require an immediate full recompute strategy.

### 4. Prompt versioning (Enterprise; avoid hardcoding forever)

Version prompts (`v1`, `v2`, ...) so quality regressions can be rolled
back.

### 5. Multi-LLM support (MVP abstraction; more providers later)

Do not couple business logic to Qwen or Ollama. Keep an LLM interface:

```text
LLM Interface → Ollama | OpenAI | Gemini | Anthropic | ...
```

MVP implements Ollama + Qwen behind the interface.

### 6. Knowledge layer and OKF-inspired portability (Production / Enterprise)

Inspired by Google's **Open Knowledge Format (OKF)** idea: a standard,
vendor-neutral way to represent knowledge (e.g. Markdown + YAML front
matter + linking conventions) that humans and AI agents can both use.
SecondBrain does **not** build around OKF on day one, but **borrows the
philosophy** and designs so OKF-style support can be added later.

#### MVP vs later

-   **MVP:** PDF / TXT / Markdown → parse → chunk → embed → vector
    search → LLM. Knowledge lives primarily in the database and local
    file storage as source documents and chunks.
-   **Later:** also maintain structured, portable **knowledge articles**
    that humans can read/edit and agents can consume without vendor
    lock-in.

#### Evolving internal project docs (optional practice)

Prefer splitting large living docs over one giant file, with YAML front
matter where useful:

```text
docs/
    architecture.md
    database.md
    api.md
    security.md
    coding-standards.md
    rag.md
    deployment.md
```

Example front matter:

```yaml
---
title: Database Design
type: Architecture
version: 1.0
owner: Rohit
updated: 2026-07-14
tags:
  - postgres
  - pgvector
  - database
---
```

#### User knowledge evolution

Today (MVP path):

```text
User → PDF → Chunks → Embeddings → Vector Search → LLM
```

Target knowledge layer (later):

```text
PDF
  → Chunks
  → Knowledge Articles (Markdown + metadata)
  → Knowledge Graph (future)
```

Example generated article shape:

```text
knowledge/
    database.md   # summary, key concepts, related topics, references to Database.pdf
```

The system then has multiple views of the same information: raw source,
chunks, embeddings, and structured knowledge.

#### Producer / consumer independence

```text
PDF → Knowledge → Any AI Model
```

Knowledge is the portable product. The LLM is a replaceable consumer.
This matches the LLM interface abstraction already planned for MVP.

#### Future `knowledge/` module (not MVP)

```text
knowledge/
    generator/   # Markdown knowledge articles from uploaded documents
    exporter/    # Export workspace as OKF-style bundle
    importer/    # Import an existing knowledge bundle
    markdown/    # Markdown + front matter conventions
```

Responsibilities:

-   Generate knowledge articles from source documents
-   Export a workspace as a portable knowledge bundle
-   Import an existing bundle
-   Keep generated knowledge synchronized with source documents

Maturity tags:

-   **Production:** generate Markdown knowledge articles; optional
    export of human-readable knowledge
-   **Enterprise:** full OKF-style import/export, sync guarantees,
    knowledge graph over articles

------------------------------------------------------------------------

## Other Long-Term Goals

-   Cloud deployment
-   Object storage (beyond local filesystem)
-   Workspace sharing
-   Dedicated embedding service
-   Horizontal scalability
-   Portable knowledge export / OKF-style interop

------------------------------------------------------------------------

This is a living document. Update it whenever an architectural decision
changes.
