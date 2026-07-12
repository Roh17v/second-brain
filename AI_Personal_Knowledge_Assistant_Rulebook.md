# AI Personal Knowledge Assistant --- Engineering Rulebook

> Version: 0.1 (Living Document)

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

Frontend - React + TypeScript

Backend - Spring Boot

Database - PostgreSQL - pgvector

LLM - Ollama - Qwen 3 4B

Storage - Local storage initially

------------------------------------------------------------------------

# Architectural Principles

1.  Controllers are thin.
2.  Business logic belongs in the application/service layer.
3.  Infrastructure details must be abstracted behind interfaces.
4.  Business logic must not depend directly on Ollama, PostgreSQL, or
    local storage.
5.  Prefer dependency inversion.
6.  Design modules so providers can be replaced with minimal changes.

------------------------------------------------------------------------

# Project Structure

-   common/
-   auth/
-   users/
-   workspace/
-   documents/
-   chat/
-   search/
-   ai/
-   storage/
-   infrastructure/
-   config/

Organize primarily by feature, while keeping clean architecture
boundaries within each feature.

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

# Current Technology Decisions

Frontend: - React - TypeScript

Backend: - Java 21 - Spring Boot

Database: - PostgreSQL - pgvector

Local LLM: - Ollama - Qwen 3 4B

Storage: - Local filesystem

Authentication: - JWT

------------------------------------------------------------------------

# Long-Term Goals

-   Cloud deployment
-   Object storage
-   Streaming responses
-   Workspace sharing
-   Multiple LLM providers
-   Dedicated embedding service
-   Production monitoring
-   Horizontal scalability

------------------------------------------------------------------------

This is a living document. Update it whenever an architectural decision
changes.
