# CLAUDE.md

## Project
QueryMind — A schema-aware Natural Language to SQL analytics engine.
Users type questions in plain English (or Turkish), the system translates them into executable PostgreSQL SELECT queries via GPT-4o, runs them against the Olist e-commerce dataset (100K+ orders), and renders results as interactive charts, tables, or KPI cards with AI-generated insight summaries.

**For whom:** Portfolio project targeting AI Engineer / BI Engineer / Java Backend internship applications.
**Problem solved:** Enabling non-technical users to analyze data without writing SQL — and demonstrating the ability to build the system that makes this possible.

## Tech Stack
- **Backend:** Java 21, Spring Boot 3.2.x, Maven, Spring Data JPA + JdbcTemplate
- **Database:** PostgreSQL 16 (dual datasource: admin + readonly)
- **AI:** OpenAI GPT-4o (NL→SQL generation + insight generation)
- **Frontend:** Next.js 14 (App Router), TypeScript, Tailwind CSS, Recharts, Zustand, TanStack Table
- **Infra:** Docker Compose (single command startup), optional Railway + Vercel deploy

## Architecture Summary
Monolithic backend (Spring Boot) + SPA frontend (Next.js). The frontend POSTs a natural language question to the backend. The backend fetches schema metadata from cache, constructs a GPT-4o prompt, validates the returned SQL, and executes it against a read-only datasource. Chart type is inferred from the result shape using deterministic rules. The response includes pre-processed chart data, raw data, and the generated SQL. A second GPT-4o call powers on-demand "Explain this result" insights. All queries are persisted to a history table.

## Rules
1. **ALWAYS PLAN FIRST** — Explain your approach before writing code
2. **NO OVERENGINEERING** — 5-day scope. Don't add anything that isn't needed right now. Stretch goals come after MVP.
3. **SMALL CHUNKS** — Each task covers a single responsibility. "Implement all of Day X" is forbidden.
4. **EXPLAIN DECISIONS** — For every significant technical decision:
   - Why we chose this approach
   - What the alternatives were
   - What the tradeoffs are
   - Under what conditions the decision would change
5. **UPDATE DOCS** — Update progress.md at the end of every session

## Boundaries

### Backend (Spring Boot)
- **Does:** REST API (query, insight, history, schema endpoints), SQL validation, query execution, schema introspection, chart type inference, rate limiting, error handling
- **Does not:** Authentication/authorization (none, public API), WebSocket, async processing (entire flow is synchronous), caching beyond the schema_cache table

### Frontend (Next.js)
- **Does:** Query input + example chips, dynamic chart rendering (bar/line/pie/kpi/table), query history sidebar, SQL display, insight badge, loading states, CSV export
- **Does not:** Server-side data fetching (all data fetched client-side), user accounts, dashboard persistence, real-time updates

### AI Layer (GPT-4o)
- **Used for:** NL→SQL translation (primary), insight generation (secondary, on-demand)
- **Not used for:** Chart type inference (rule-based), SQL validation (regex-based), schema introspection (direct DB metadata query)

## Current State
**Status:** Day 0 — Project has not started yet. PRD is complete, docs are being prepared.
**Next task:** Docker Compose + PostgreSQL setup, Olist dataset loading, Spring Boot project scaffolding.