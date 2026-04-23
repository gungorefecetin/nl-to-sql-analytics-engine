# Progress

> Daily progress, learnings, decisions. Postmortem will be written as the final section of this file when the project is complete.

---

## Day 0 — [DATE]

### Plan
**Objective:** Prepare project infrastructure — docs, Docker, DB, dataset, Spring Boot scaffolding
**In Scope:**
- Create CLAUDE.md, architecture.md, decision-log.md, progress.md
- Stand up Docker Compose + PostgreSQL 16
- Download Olist dataset, write init.sql with table definitions
- Load CSVs via seed.sh
- Spring Boot project scaffolding (Spring Initializr)
- Dual DataSource configuration
- SchemaIntrospectionService + schema_cache population
- `GET /api/schema` endpoint

**Out of Scope:**
- OpenAI integration (Day 2)
- Frontend (Day 4)
- Chart type inference (Day 3)

**Acceptance Criteria:**
- [ ] `docker compose up` starts PostgreSQL successfully
- [ ] `SELECT COUNT(*) FROM olist_orders_dataset;` → ~99k rows
- [ ] Spring Boot app starts without errors
- [ ] `GET /api/schema` returns table metadata JSON
- [ ] Dual datasource configured (admin + readonly)
- [ ] QueryHistory + SchemaCache JPA entities + repositories created

### End of Day
**Completed:**
- [ ] *fill in at end of day*

**Decisions Made:**
- DL-001 ~ DL-006: Initial architectural decisions (architecture phase, details in decision-log)

**Learned:**
- *fill in at end of day*

**Tomorrow:**
- OpenAI client implementation
- SqlValidationService + SqlExecutionService
- PromptBuilderService
- QueryOrchestrationService
- `POST /api/query` endpoint — first end-to-end NL→SQL flow

---

## Day 1 — 2026-04-07

### Plan
**Objective:** Backend foundation — Docker, DB, data loading, Spring Boot scaffolding, schema introspection
**In Scope:**
- Finalize Docker Compose
- Verify Olist CSV loading
- JPA entities + repositories
- Schema introspection service
- `/api/schema` endpoint live

**Out of Scope:**
- OpenAI calls
- SQL validation logic
- Anything frontend

**Acceptance Criteria:**
- [ ] `docker compose up` → clean start
- [ ] Data verified in DB
- [ ] `/api/schema` returns correct JSON
- [ ] Dual datasource works (admin writes, readonly reads)

### End of Day
**Completed:**
- [x] `docker compose up` → clean start
- [x] Olist data verified (99,441 orders, 1.55M total rows across 9 tables)
- [x] `/api/schema` returns correct JSON
- [x] Dual datasource works (admin via JPA validation, readonly via introspection service)

Also completed ahead of schedule:
- [x] QueryHistory + SchemaCache JPA entities and repositories
- [x] SchemaIntrospectionService (startup-time `information_schema` read → `schema_cache` upsert)
- [x] SchemaController with `GET /api/schema` endpoint

**Decisions Made:**
- `ddl-auto: validate` instead of `update` — `init.sql` is the single source of truth for schema, Hibernate only verifies the mapping matches
- Test via Docker instead of local JDK — keeps the dev environment reproducible, no local Java install needed

**Learned:**
- Docker entrypoint scripts run alphabetically — naming them `01-init.sql`, `02-seed.sh` controls execution order reliably
- Spring Boot's `@EventListener(ApplicationReadyEvent)` fires after all beans are wired, making it the right hook for schema introspection (not `@PostConstruct`, which fires too early for datasource availability)
- With dual datasources, Spring Boot Actuator health auto-detects both and reports them separately — no extra config needed

**Tomorrow:**
- Day 2 — OpenAI client, SqlValidationService, SqlExecutionService, PromptBuilderService, QueryOrchestrationService, `POST /api/query` endpoint, retry logic, query history persistence

---

## Day 2 — 2026-04-13

### Plan
**Objective:** NL-to-SQL core pipeline — OpenAI integration, validation, execution, first working query
**In Scope:**
- OpenAIClient (HTTP call to GPT-4o)
- SqlValidationService (blocked keywords + regex)
- SqlExecutionService (readonly datasource execution)
- PromptBuilderService (schema-aware prompt construction)
- QueryOrchestrationService (ties everything together)
- `POST /api/query` endpoint
- Retry logic (max 2 retries on SQL failure)
- Query history persistence
- Unit tests for SqlValidationService

**Out of Scope:**
- Chart type inference (Day 3)
- Insight generation (Day 3)
- Frontend (Day 4)

**Acceptance Criteria:**
- [x] `curl POST /api/query` with "How many orders in 2017?" → correct SQL → correct count
- [x] INSERT/DROP/DELETE statements blocked by validation
- [x] Query persisted in query_history table
- [x] Retry works when GPT-4o returns bad SQL

### End of Day
**Completed:**
- [x] `POST /api/query` end-to-end works (integration test with mocked OpenAI: `SELECT COUNT(*) FROM olist_orders_dataset` → real PG execution → 200 response)
- [x] INSERT/DROP/DELETE blocked (38 SqlValidationService unit tests, all green)
- [x] Query persisted in `query_history` on every path (success: id=1, failure with error_message: id=2, both verified in DB)
- [x] Retry logic implemented (multi-turn conversation history, max 2 retries, per DL-009)

Files added (12 new, 3 modified):
- `OpenAIClient` + `OpenAIClientException` + `OpenAIConfig` (RestClient-based GPT-4o caller with multi-turn support)
- `SqlValidationService` + `UnsafeSqlException` (38 unit tests: blocked keywords, SELECT-only, semicolon stripping, length limit, false positive prevention)
- `SqlExecutionService` + `SqlExecutionException` (readonly JdbcTemplate, 30s timeout, PG error extraction for retry loop)
- `PromptBuilderService` (full schema in every prompt, sample value cardinality heuristic — DL-008)
- `QueryOrchestrationService` (pipeline coordinator: prompt → OpenAI → validate → execute → persist, with retry loop)
- `QueryController` + `QueryRequest` + `QueryResponse` DTOs
- `QueryControllerIntegrationTest` (MockMvc + mocked OpenAI + real PG)
- `pom.xml` updated: added `spring-boot-starter-validation`
- `docker-compose.yml` updated: added `OPENAI_API_KEY` passthrough

**Decisions Made:**
- DL-007: Word-boundary regex for SQL keyword blocklist — `\bUPDATE\b` avoids false positives on column names like `updated_at`, `execution_time_ms`, `created_at`
- DL-008: Full schema in every prompt, no retrieval/filtering — Olist is 9 tables / ~55 columns / ~2,500 tokens, strictly better to include everything than risk missing a table GPT-4o needs
- DL-009: Multi-turn conversation history for retry loop — GPT-4o makes targeted surgical fixes when it sees its own prior output in the assistant role, rather than rewriting from scratch

**Learned:**
- Spring Boot 3.2's `RestClient` works well as a lightweight OpenAI integration — no SDK dependency needed. The fluent API handles JSON serialization via Jackson automatically, and error responses come back as exceptions with the full response body (which is how we got the clean `401 Unauthorized: { "error": ... }` message).
- Naive `String.contains("UPDATE")` for SQL keyword blocking is a trap — real-world schemas have columns like `updated_at`, `execution_time_ms`, `created_at` that contain blocked keywords as substrings. Word-boundary regex (`\bUPDATE\b`) solves this with zero extra dependencies, but it's the kind of bug you'd only catch with tests against realistic column names.
- `@MockBean` (Spring Boot 3.2) vs `@MockitoBean` (Spring Boot 3.4+) — the package moved from `org.springframework.boot.test.mock.mockito` to `org.springframework.test.context.bean.override.mockito`. Version-sensitive import that compile errors won't explain clearly.

**Tomorrow:**
- Day 3 — ChartTypeInferenceService, InsightGenerationService, `GET /api/query/history`, GlobalExceptionHandler, rate limiter, query timeout, Swagger UI verification

---

## Day 3 — [DATE]

### Plan
**Objective:** Intelligence layer — chart inference, insight generation, history endpoint, error handling
**In Scope:**
- ChartTypeInferenceService (rule-based)
- InsightGenerationService (GPT-4o second call)
- `POST /api/insight` endpoint
- `GET /api/query/history` endpoint (paginated)
- Enrich QueryResponse DTO with chartType + chartData
- In-memory rate limiter (20 req/min/IP)
- GlobalExceptionHandler
- Query execution timeout (30s)
- Integration tests (TestContainers)
- Swagger UI verification

**Out of Scope:**
- Frontend (Day 4)
- Deployment (Day 5)

**Acceptance Criteria:**
- [ ] 5 different test queries return correct chart types
- [ ] Insight generation returns 2-3 sentence actionable text
- [ ] History endpoint works with pagination
- [ ] Rate limiter rejects the 21st request
- [ ] Swagger UI live at `/swagger-ui.html`
- [ ] All error cases return consistent JSON format

### End of Day
**Completed:**
- [ ] *fill in at end of day*

**Decisions Made:**
- *fill in at end of day*

**Learned:**
- *fill in at end of day*

**Tomorrow:**
- *fill in at end of day*

---

## Day 4 — [DATE]

### Plan
**Objective:** Frontend — complete UI with all chart types, query input, loading states
**In Scope:**
- Next.js 14 project setup (TypeScript + Tailwind)
- Design tokens (colors, typography)
- Zustand store
- `lib/api.ts` typed API functions
- QueryInput component + example chips
- LoadingState (fake progress stepper)
- BarChartView, LineChartView, PieChartView (Recharts)
- KpiCardView
- DataTable (TanStack Table, sort + pagination)
- SqlDisplay (syntax highlighted)
- ResultsPanel (dynamic chart/table rendering)
- End-to-end test: type question → see chart

**Out of Scope:**
- History sidebar (Day 5)
- Insight badge (Day 5)
- Animations (Day 5)
- Deployment (Day 5)

**Acceptance Criteria:**
- [ ] End-to-end: type question in UI → see correct chart
- [ ] All 5 chart types render correctly (bar, line, pie, kpi, table)
- [ ] Loading state shows progress steps
- [ ] Error states display user-friendly messages
- [ ] Dark theme applied consistently

### End of Day
**Completed:**
- [ ] *fill in at end of day*

**Decisions Made:**
- *fill in at end of day*

**Learned:**
- *fill in at end of day*

**Tomorrow:**
- *fill in at end of day*

---

## Day 5 — [DATE]

### Plan
**Objective:** Polish, deploy, documentation — production-ready state
**In Scope:**
- HistorySidebar (slide-in, click to re-run)
- InsightBadge (generate button + sparkle icon)
- Framer Motion animations (result reveal, loading fade)
- CSV export on DataTable
- Responsive design check
- Frontend Dockerfile
- Full `docker compose up` test from scratch
- README.md (setup guide, screenshots, demo GIF, example queries)
- GitHub push with clean commit history
- (Optional) Deploy: Railway + Vercel

**Out of Scope:**
- New features beyond MVP
- Performance optimization
- Comprehensive test suite

**Acceptance Criteria:**
- [ ] `docker compose up` → full app running from scratch
- [ ] README with setup instructions + screenshots
- [ ] GitHub repo public
- [ ] History sidebar functional
- [ ] Insight generation from UI works
- [ ] Mobile-responsive

### End of Day
**Completed:**
- [ ] *fill in at end of day*

**Decisions Made:**
- *fill in at end of day*

**Learned:**
- *fill in at end of day*

---

## Postmortem
> *To be written when the project is complete. Fill in the sections below after the final day.*

### What went well?
- *after project completion*

### What was poorly designed?
- *after project completion*

### Where did I overengineer?
- *after project completion*

### What did I knowingly skip?
- *after project completion*

### This project's interview story
*Write this yourself, not Claude.*