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

## DL-007: Word-Boundary Regex for SQL Keyword Blocklist

**Date:** Day 2 (Implementation)
**Context:** `SqlValidationService` blocks dangerous SQL keywords (INSERT, UPDATE, DELETE, DROP, CREATE, ALTER, etc.) in AI-generated SQL. Naive substring matching (`String.contains()`) would false-positive on legitimate column names: `updated_at` contains UPDATE, `execution_time_ms` contains EXECUTE, `created_at` contains CREATE, `is_deleted` contains DELETE. These columns exist in the Olist dataset and app tables.

**Decision:** Use `\b` word boundaries for SQL keyword matching (`\bUPDATE\b`, `\bDELETE\b`), and literal `Pattern.quote()` matching for symbol/function tokens (`--`, `/*`, `xp_`, `pg_read_file`). Both are compiled as case-insensitive `Pattern` objects at class load time.

**Alternatives:**
- **Naive substring matching (`toUpperCase().contains()`):** Zero-dependency, trivial to implement. But produces false positives on real column names — `SELECT updated_at FROM schema_cache` would be incorrectly rejected.
- **SQL parser (JSqlParser):** Would correctly distinguish keywords from identifiers in all cases, including creative obfuscation. But it's a heavyweight dependency (~1.5MB), overkill for a blocklist at the boundary where a read-only DB role is the true security layer.
- **Tokenizer approach (split on whitespace/punctuation, check tokens):** No regex, but fragile — fails on `UPDATE(` (no space) or quoted identifiers.

**Tradeoffs:**
- (+) Zero false positives on real column names — verified with tests for `execution_time_ms`, `updated_at`, `created_at`, `is_deleted`
- (+) Zero extra dependencies — uses `java.util.regex` only
- (+) Patterns compiled once at class load, negligible runtime cost
- (−) Regex-based, not a true SQL parser — wouldn't catch creative obfuscation (e.g., `UP/**/DATE`) that a parser would
- (−) Word boundary `\b` depends on `\w` character class — safe for SQL keywords (all alphabetic) but wouldn't work for tokens containing special chars (handled separately with literal matching)

**Reversal trigger:** If we ever allow user-provided SQL (currently AI-only, so the attack surface is limited to prompt injection) or if the read-only DB role (DL-001) is removed, swap to a real SQL parser like JSqlParser. The regex approach is sufficient as one layer of defense-in-depth, not as the sole security boundary.

---

## DL-008: Full Schema in Every Prompt (No Retrieval / No Filtering)

**Date:** Day 2 (Implementation)
**Context:** `PromptBuilderService` constructs the GPT-4o prompt for NL→SQL translation. Key question: which tables to include in the prompt? The Olist dataset has 9 tables with ~55 total columns. The full schema formatted as text is ~1,200–1,500 tokens — about 1.7% of GPT-4o's 128K context window.

**Decision:** Include all 9 Olist tables in every prompt. Cap sample values per column to distinct values from 3 sample rows, only included if the column has ≤10 distinct values in the sample (enum-like heuristic). Total prompt size: ~2,200 tokens. No truncation or dynamic budget needed.

**Alternatives:**
- **Keyword-based table filtering:** Match question words to table/column names, only include "relevant" tables. Fragile — "which cities have the most sellers?" needs customers + orders + sellers, hard to predict from keywords alone. Risk of missing a required table is high.
- **Two-pass LLM call (classify then generate):** First GPT-4o call selects relevant tables, second generates SQL. More accurate filtering, but doubles latency (~4-8s → ~8-16s), breaks the <8s target (DL-002).
- **Embedding similarity:** Pre-embed table descriptions, find nearest match to the question. Over-engineered for 9 tables — the embedding/retrieval infrastructure costs more than the tokens saved.

**Tradeoffs:**
- (+) Zero risk of missing a required table — GPT-4o is good at ignoring irrelevant context, but bad at generating SQL for tables it can't see
- (+) Single LLM call preserves the latency budget (<8s target)
- (+) Simplest implementation — no retrieval pipeline, no classification step
- (−) Wastes ~2K tokens per call on irrelevant context (trivial at GPT-4o pricing: <$0.001/query)
- (−) Won't scale to 50+ table datasets without revisiting

**Reversal trigger:** If the dataset grows past ~20 tables or prompt exceeds ~4,000 tokens, add a table selection step (keyword-based first, embedding-based if that proves insufficient).

---

## DL-009: Multi-Turn Conversation History for SQL Retry Loop

**Date:** Day 2 (Implementation)
**Context:** When AI-generated SQL fails execution, the orchestrator re-calls GPT-4o with the PostgreSQL error message to request a correction. Two approaches: (A) multi-turn conversation where the assistant's original bad SQL appears in conversation history, followed by a user message with the error, or (B) single-turn with the error folded into a new flat prompt.

**Decision:** Option A — multi-turn conversation history. `OpenAIClient` gains a `Message` record and an overloaded `chatCompletion(systemPrompt, List<Message>)` method. The original two-string `chatCompletion(system, user)` stays as a convenience that delegates to the new method. The orchestrator builds up the message list across retries: user prompt → assistant SQL → user error correction → assistant fixed SQL → ...

**Alternatives:**
- **Option B (flat prompt with error appended):** Simpler interface — `OpenAIClient` stays as-is, the orchestrator appends `"\n\nPREVIOUS ATTEMPT:\n{sql}\n\nERROR: {pg_error}\n\nFix the SQL query."` to the user prompt string. Lower token cost per retry. But the model may rewrite the query from scratch instead of making a targeted fix, potentially introducing new errors.

**Tradeoffs:**
- (+) GPT-4o produces more surgical fixes when it sees its own prior output in the assistant role — this is the recommended multi-turn pattern for error correction
- (+) Each retry preserves the full conversation context, so the model doesn't repeat the same mistake
- (−) ~2-3x token cost on retries (negligible at GPT-4o pricing — ~$0.001 per retry, max 2 retries)
- (−) `OpenAIClient` interface gains complexity (overloaded method + Message record)

**Reversal trigger:** If production logs show GPT-4o consistently rewriting queries from scratch on retry (ignoring its own prior output), simplify to Option B — the extra interface complexity isn't paying for itself.

---

## DL-010: ResultSetExtractor for Column Metadata + Rows

**Date:** Day 3 (Implementation)
**Context:** `ChartTypeInferenceService` needs column names and JDBC types to decide chart type. The original `SqlExecutionService` used `JdbcTemplate.queryForList()` which returns `List<Map<String, Object>>` — column types and ordering are lost. Computed columns like `SUM(price) AS "Total Revenue"` have no type information, and `HashMap` key ordering is not guaranteed.

**Decision:** Switch to `ResultSetExtractor`. Return a `QueryResult` record with `List<ColumnMeta(name, jdbcType)>` + `List<Map<String, Object>> rows`. Use `getColumnLabel()` (not `getColumnName()`) so aliases like `"Total Revenue"` are preserved. Store `jdbcType` as raw `int` from `java.sql.Types`.

**Alternatives:**
- **RowMapper with stateful metadata capture:** `RowMapper`'s contract is "map one row." Capturing `ResultSetMetaData` on the first invocation works mechanically but mixes concerns — the mapper becomes a side-effect-laden metadata extractor that happens to also map rows. Semantically wrong.
- **`queryForList` + infer types from Java object classes:** Inspect `row.get(key).getClass()` to determine if a column is numeric, string, or temporal. Loses column ordering (HashMap), breaks on all-null columns (no object to inspect), and throws away JDBC metadata we already have inside the ResultSet.

**Tradeoffs:**
- (+) Single pass through the ResultSet — metadata and rows extracted in one callback
- (+) Preserves column ordering (metadata reports columns in SELECT order)
- (+) Handles computed columns correctly — `SUM(price)` reports as `NUMERIC`, not unknown
- (+) Null values still have correct type info (metadata is independent of row values)
- (−) Slightly more ceremony than `queryForList` — manual row iteration instead of one-liner
- (−) `QueryResult` record touches multiple services (orchestrator, controller) — but the change is mechanical

**Reversal trigger:** None expected. `ResultSetExtractor` is the correct Spring JDBC pattern when you need both metadata and rows from a single query execution.

---

## DL-011: Store Result Data in query_history (JSONB)

**Date:** Day 3 (Implementation)
**Context:** Insight generation needs the first 5 rows of query results. Options: store at query time or re-execute SQL at insight time.

**Decision:** Add nullable `result_data` JSONB column to `query_history`. The orchestrator stores the first 5 rows at persist time. `InsightGenerationService` reads from this column — no dependency on `SqlExecutionService`.

**Alternatives:**
- **Re-execute stored SQL at insight time:** Avoids schema change, but adds 50-500ms latency (JOIN/GROUP BY re-execution), couples insight generation to the SQL execution pipeline, and breaks determinism if data changes between query and insight request.

**Tradeoffs:**
- (+) Zero additional latency for insights — data is already persisted
- (+) Insight describes the results the user actually saw, not a re-execution that could differ
- (+) `InsightGenerationService` has no dependency on `SqlExecutionService` — clean separation
- (−) Storage growth (~500 bytes per row for 5 rows of JSON)
- (−) Schema migration needed (one nullable JSONB column)

**Reversal trigger:** If `result_data` storage becomes a space concern (unlikely — 500 bytes × thousands of queries is negligible) or if real-time re-execution is needed for mutable datasets.

---

*New decisions will be added during the build. The "Reversal trigger" section of each entry is especially important — it's a ready-made answer for "when would you change this decision?" in interviews.*