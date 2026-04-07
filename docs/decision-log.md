# Decision Log

> Every architectural decision lives here. Routine CRUD/UI decisions don't belong.
> Format: Context → Decision → Alternatives → Tradeoffs → Reversal Trigger

---

## DL-001: Dual DataSource — Admin + Read-Only PostgreSQL Roles

**Date:** Day 0 (Architecture)
**Context:** We execute GPT-4o-generated SQL directly against PostgreSQL. This is a serious security surface — prompt injection could produce write/drop statements. Multiple defense layers are needed.

**Decision:** Two separate datasources: `querymind_admin` (r/w for JPA entities) + `querymind_readonly` (SELECT-only for AI-generated SQL execution). Separated in Spring via `@Qualifier`.

**Alternatives:**
- **Single datasource + regex validation only:** Simpler config, but if regex is bypassed the entire DB is exposed. Single point of failure.
- **Single datasource + DB-level row security:** PostgreSQL RLS could be used, but it doesn't cleanly prevent writes on olist tables. A separate role is a cleaner solution.

**Tradeoffs:**
- (+) Defense in depth — even if the validation layer is bypassed, the DB role blocks writes
- (+) Strong interview talking point around "defense in depth" security architecture
- (−) Two connection pools to manage, config complexity increases
- (−) Configuring dual datasources in Spring requires some boilerplate

**Reversal trigger:** If the project scope simplifies to the point where a single read-only endpoint structure is sufficient (unlikely for this project).

---

## DL-002: Synchronous Request Flow (No Async/Streaming)

**Date:** Day 0 (Architecture)
**Context:** User query → GPT-4o call (~2-4s) → SQL execution (~100-500ms) → response. Total target < 8s. Async or streaming would improve UX but adds complexity.

**Decision:** Entire pipeline runs synchronously within a single HTTP request. Frontend uses a fake progress stepper (setTimeout-based step transitions) to improve perceived responsiveness.

**Alternatives:**
- **SSE (Server-Sent Events):** Pipe GPT-4o streaming response to the client. Show real progress as SQL is being generated. But Spring Boot + SSE + frontend handling = 1-2 extra days of work.
- **WebSocket:** Overkill — no need for bidirectional communication.
- **Polling:** Client submits, gets a job ID, polls for completion. Worst UX, unnecessary complexity.

**Tradeoffs:**
- (+) Simple implementation, fits the 5-day scope
- (+) Error handling is straightforward — single try/catch, single response
- (−) User sees no real progress for 5-8 seconds (masked by fake stepper)
- (−) If GPT-4o slows down (>10s), UX degrades; timeout is necessary

**Reversal trigger:** If GPT-4o latency consistently exceeds 8s, or if a multi-step pipeline is added (e.g., query plan → approval → execution), switch to SSE.

---

## DL-003: Rule-Based Chart Type Inference

**Date:** Day 0 (Architecture)
**Context:** We need to decide which chart type to use for each SQL result. Two approaches: ask the LLM, or apply rules based on the result shape.

**Decision:** Deterministic if/else rules — inspect column count, types, and row count. 1 row + 1 numeric = KPI, date column + numeric = line, string + numeric ≤ 20 rows = bar, share/percent context = pie, fallback = table.

**Alternatives:**
- **Ask GPT-4o:** Add "which chart type?" to the prompt. Smarter decisions, but adds ~1-2s latency + additional API cost per query.
- **Hybrid approach:** Rule-based first, LLM fallback if confidence is low. Best of both worlds but increases complexity.

**Tradeoffs:**
- (+) Zero latency, zero cost — rules execute instantly
- (+) Deterministic — same result shape always produces the same chart, easy to debug
- (+) Strong interview answer for "why didn't you use LLM?": "LLM is overkill; simple pattern matching is sufficient"
- (−) Edge cases: "revenue by month" — bar or line? Depends on whether the month column is a string or a date
- (−) Adding a new chart type means adding new rules (but 5 types is enough for MVP)

**Reversal trigger:** If incorrect chart type inference rate exceeds ~20% (measured through user feedback or manual testing), switch to hybrid approach.

---

## DL-004: Schema Cache — Startup Introspection + DB Persist

**Date:** Day 0 (Architecture)
**Context:** Every GPT-4o prompt needs database schema context — table names, column types, sample values. Should we query this fresh per request, or cache it?

**Decision:** On application startup, run `information_schema.columns` + sample data queries, persist results to the `schema_cache` table as JSONB. Read from cache for every prompt build.

**Alternatives:**
- **Query information_schema per request:** Simple, always fresh. But adds ~50ms per request, and Olist schema is static anyway.
- **In-memory cache (HashMap):** Don't persist to DB, just hold in memory. Faster reads but lost on app restart, requires re-introspection. We already do that on startup anyway, but DB persistence makes it inspectable via Swagger.
- **Static JSON file:** Hardcode the schema. Fastest possible, but zero maintainability — any table change requires a file update.

**Tradeoffs:**
- (+) Runs once at startup, every prompt build after that is ~5ms (DB read)
- (+) Inspectable via `GET /api/schema` endpoint, useful for debugging
- (+) If dynamic dataset upload is added later, only cache invalidation needs to be implemented
- (−) Startup time increases slightly (information_schema + sample data queries)
- (−) Schema changes require app restart (but Olist is static, so this is a non-issue)

**Reversal trigger:** If dynamic dataset upload (user uploads their own CSV) is implemented → cache TTL + invalidation mechanism needed.

---

## DL-005: Zustand (State Management)

**Date:** Day 0 (Architecture)
**Context:** Frontend needs global state: current query, loading, result, error, history. Which state management approach to use?

**Decision:** Zustand — single store, minimal API, no boilerplate.

**Alternatives:**
- **React Context + useReducer:** Built-in, no dependency. But provider nesting and re-render optimization are manual.
- **Redux Toolkit:** Industry standard, but overkill for this scope — slice, thunk, selector boilerplate is unnecessary.
- **Jotai/Recoil:** Atomic state model. Elegant but app state isn't complex enough to warrant splitting into atoms.

**Tradeoffs:**
- (+) ~1KB gzipped, minimal bundle impact
- (+) DevTools middleware, persist middleware available if needed later
- (+) API is dead simple — `create()` + `useStore()`, 5-minute setup
- (−) Extra dependency (though micro-sized)
- (−) Context API would have been sufficient — but Zustand demonstrates state management competence on a CV

**Reversal trigger:** None — no reason to switch away from Zustand within this scope.

---

## DL-006: GPT-4o (Model Selection)

**Date:** Day 0 (Architecture)
**Context:** Which LLM to use for NL→SQL translation? Accuracy is critical — incorrect SQL means bad UX.

**Decision:** OpenAI GPT-4o — best SQL generation accuracy, fast enough (~2-4s), well-documented API.

**Alternatives:**
- **GPT-4o-mini:** Cheaper, faster (~1-2s). But SQL accuracy is lower — error rate increases with complex JOINs.
- **Claude (Anthropic):** Competitive in SQL generation. But OpenAI SDK is more mature in the Java ecosystem, and the project is already positioned as "OpenAI integration" on the CV.
- **Open-source (Llama 3, CodeLlama):** Requires self-hosting → infrastructure complexity, impossible within a 5-day scope.

**Tradeoffs:**
- (+) Highest SQL accuracy, reliable with complex JOINs/GROUP BY/subqueries
- (+) Well-documented Java SDK/HTTP client
- (+) "GPT-4o integration" looks strong on a CV
- (−) API cost (~$0.01-0.03 per query) — roughly $5-10 budget during development
- (−) Vendor lock-in — but an abstraction layer makes switching providers straightforward

**Reversal trigger:** If API cost becomes unacceptable → fall back to GPT-4o-mini. If accuracy is insufficient → improve prompt engineering first, switch models second.

---

*New decisions will be added during the build. The "Reversal trigger" section of each entry is especially important — it's a ready-made answer for "when would you change this decision?" in interviews.*