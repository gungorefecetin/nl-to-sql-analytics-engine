# Architecture

## System Overview
QueryMind is a 3-layer monolithic application: Next.js SPA (presentation) → Spring Boot REST API (orchestration + business logic) → PostgreSQL (data + app state). The AI layer lives inside the backend and makes outbound calls to the OpenAI API. The entire flow is synchronous — a request comes in, GPT-4o is called, SQL is executed, and the response is returned. No async processing.

## Component Diagram

```
Browser (Next.js SPA)
│
│  POST /api/query        GET /api/query/history
│  POST /api/insight       GET /api/schema
▼
┌─────────────────────────────────────────────────────┐
│              SPRING BOOT BACKEND                     │
│                                                       │
│  QueryController ─── QueryOrchestrationService       │
│  InsightController        │                          │
│  SchemaController         ├── PromptBuilderService   │
│                           ├── SqlValidationService   │
│                           ├── SqlExecutionService    │
│                           ├── ChartTypeInference     │
│                           └── OpenAIClient           │
│                                                       │
│  DataSourceConfig: PRIMARY (r/w) + READONLY (r/o)    │
│  GlobalExceptionHandler + Rate Limiter               │
└──────────┬──────────────────────┬────────────────────┘
           │                      │
           ▼                      ▼
    ┌──────────────┐    ┌─────────────────────────────┐
    │  OpenAI API  │    │       PostgreSQL 16          │
    │  (GPT-4o)    │    │                             │
    │              │    │  olist_* (9 tables)          │
    │  - NL→SQL    │    │  query_history               │
    │  - Insights  │    │  schema_cache                │
    └──────────────┘    └─────────────────────────────┘
```

## Request Flow (Critical Paths)

### Path 1: Natural Language → SQL → Result
1. Client sends `POST /api/query` with `{ naturalLanguage: "..." }`
2. `QueryController` validates input (length ≤ 500 chars)
3. `QueryOrchestrationService` kicks off the pipeline:
   - `SchemaIntrospectionService` → pulls all Olist table metadata from schema_cache
   - `PromptBuilderService` → constructs GPT-4o prompt with schema + sample data + user question
   - `OpenAIClient` → POSTs to GPT-4o, receives SQL string
   - `SqlValidationService` → blocked keyword check (INSERT/DROP/etc), SELECT-only enforcement, semicolon stripping, max length check
   - `SqlExecutionService` → executes SQL on the **read-only datasource**, returns `List<Map<String, Object>>`
   - If SQL execution fails → retry loop: feed error message back to GPT-4o, max 2 retries
   - `ChartTypeInferenceService` → determines chart type from result shape (rule-based)
   - Creates and persists `QueryHistory` entity via JPA
4. Returns `QueryResponse` DTO: generatedSql, chartType, chartData, data, rowCount, executionTimeMs

### Path 2: Insight Generation
1. Client sends `POST /api/insight` with `{ historyId: 42 }`
2. `InsightController` → retrieves the relevant record from `QueryHistory`
3. `InsightGenerationService` → sends SQL + result data to GPT-4o with a business analyst prompt
4. Returns a 2-3 sentence actionable insight

### Path 3: Schema Metadata
1. Client sends `GET /api/schema`
2. `SchemaController` → returns JSON from the `schema_cache` table
3. Frontend doesn't consume this directly (it's a debug/Swagger endpoint)

### Path 4: Query History
1. Client sends `GET /api/query/history?page=0&size=20`
2. `QueryController` → runs paginated JPA query, returns last 50 entries
3. Frontend displays in sidebar, click to re-run

## Data Flow
- **Input:** User's natural language question (string, max 500 chars)
- **NL → SQL:** Frontend → Backend → GPT-4o API (JSON request/response)
- **SQL → Data:** Backend → PostgreSQL read-only connection → `List<Map<String, Object>>`
- **Data → Chart:** Backend infers chart type + pre-processes chartData → Frontend renders via Recharts
- **Persistence:** Every query → `query_history` table (JPA, PRIMARY datasource)
- **Schema Cache:** On app startup, `information_schema.columns` is read → persisted as JSONB in `schema_cache` table → used in every GPT-4o prompt

## Key Decisions

### DL-001: Dual DataSource (Admin + Read-Only)
- **Choice:** Two separate PostgreSQL connections — admin (r/w for JPA app tables) + readonly role (for AI-generated SQL execution)
- **Why:** Makes it physically impossible for GPT-4o-generated SQL to write data. Defense in depth — even if validation is bypassed, the DB role blocks writes.
- **Alternative:** Single datasource + regex validation only → insufficient security, one edge case away from data corruption
- **Tradeoff:** (+) Bulletproof security, (−) Config complexity increases, two connection pools to manage

### DL-002: Synchronous Flow (No Async)
- **Choice:** Entire pipeline runs synchronously within a single HTTP request. Frontend uses a fake progress stepper (setTimeout-based step transitions) to improve perceived UX.
- **Why:** 5-day scope. Async (WebSocket/SSE/polling) adds significant complexity. Target latency < 8s is achievable with sync: GPT-4o ~2-4s, SQL exec ~100-500ms.
- **Alternative:** SSE streaming (pipe GPT-4o streaming response to client) → better UX but 1-2 extra days of implementation
- **Reversal trigger:** If latency consistently exceeds 10s or a multi-step pipeline is added (e.g., query plan → approval → execution)

### DL-003: Rule-Based Chart Type Inference (Not AI)
- **Choice:** Deterministic if/else rules based on result shape — 1 row numeric = KPI, date + numeric = line, categorical + numeric = bar, share/percent context = pie, fallback = table.
- **Why:** Deterministic, fast, zero additional API cost. Chart type selection is fundamentally a simple pattern matching problem.
- **Alternative:** Ask GPT-4o "which chart type should be used?" → adds latency + cost, LLM is overkill for this
- **Reversal trigger:** If wrong chart type inference rate exceeds ~20%, consider hybrid approach (rule-based + LLM fallback)

### DL-004: Schema Cache Table (Startup Introspection)
- **Choice:** On app startup, read `information_schema.columns` + sample data, persist to `schema_cache` table as JSONB. Read from cache for every prompt build.
- **Why:** Schema is static (Olist dataset doesn't change). Querying information_schema per request would add ~50ms overhead for no benefit.
- **Alternative:** Query information_schema per request (simple but wasteful) or hardcoded JSON file (fast but unmaintainable)
- **Reversal trigger:** If dynamic dataset upload is added, cache invalidation strategy needed

### DL-005: Zustand (Frontend State Management)
- **Choice:** Zustand — single store, minimal API, no boilerplate
- **Why:** App state is simple (query, result, loading, error, history). Redux is overkill. Context API would work but Zustand is more ergonomic and shows state management competence on a CV.
- **Alternative:** React Context + useReducer (built-in, no dependency) or Redux Toolkit (industry standard but heavy for this scope)
- **Tradeoff:** (+) ~1KB gzipped, minimal boilerplate, DevTools support. (−) Extra dependency, though Context would have sufficed.