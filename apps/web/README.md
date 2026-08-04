# SecondBrain Web (`apps/web`)

React + TypeScript + Vite SPA for SecondBrain.

## Monorepo layout

```text
SecondBrain/
  apps/
    web/          ← this app
    mobile/       ← future (React Native / Expo, etc.)
  backend/        ← Spring Boot API
```

## Setup

```bash
cd apps/web
npm install
cp .env.example .env   # if needed
npm run dev
```

App: http://localhost:5173  
API: http://localhost:8080 (set `VITE_API_BASE_URL`)

## Features

- Login / register (JWT in `localStorage`)
- Workspaces list + create
- Documents: upload, process, embed
- Chat with citations (RAG)

Backend must be running with Postgres, and Ollama for embed/chat.
