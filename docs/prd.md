# QueryMind — Product Requirements Document

> **Version:** 1.0  
> **Last Updated:** 2026-02-27  
> **Author:** Güngör Efe Çetin  
> **Status:** Ready for Development  
> **Target Completion:** 5 days from start

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Goals & Success Metrics](#2-goals--success-metrics)
3. [Tech Stack & Architecture](#3-tech-stack--architecture)
4. [System Architecture Diagram](#4-system-architecture-diagram)
5. [Database Schema](#5-database-schema)
6. [Backend Specification](#6-backend-specification)
7. [Frontend Specification](#7-frontend-specification)
8. [AI & Prompt Engineering](#8-ai--prompt-engineering)
9. [Security & Input Validation](#9-security--input-validation)
10. [Error Handling Strategy](#10-error-handling-strategy)
11. [Data Pipeline & Seeding](#11-data-pipeline--seeding)
12. [Docker & Infrastructure](#12-docker--infrastructure)
13. [Testing Strategy](#13-testing-strategy)
14. [Day-by-Day Development Plan](#14-day-by-day-development-plan)
15. [File & Folder Structure](#15-file--folder-structure)
16. [Environment Variables](#16-environment-variables)
17. [API Reference](#17-api-reference)
18. [CV & Portfolio Positioning](#18-cv--portfolio-positioning)
19. [Future Enhancements (Post-MVP)](#19-future-enhancements-post-mvp)

---

## 1. Project Overview

### What is QueryMind?

QueryMind is a schema-aware Natural Language to SQL analytics engine. Users type questions in plain English (or Turkish), and the system translates them into executable SQL, runs the query against a PostgreSQL database loaded with a real-world e-commerce dataset, and renders the results as interactive charts, tables, or KPI cards with AI-generated insight summaries.

### Why does this project exist?

- To demonstrate Java (Spring Boot) + SQL skills in a portfolio context
- To showcase practical LLM integration (GPT-4o) beyond chatbot use cases
- To stand out in AI Engineer / BI Engineer internship applications
- To produce a working, deployable, visually impressive product in 4-5 days

### Dataset

**Olist Brazilian E-Commerce Dataset** (Kaggle)  
- 100,000+ orders across 9 relational tables  
- Date range: 2016–2018  
- Includes: orders, customers, products, sellers, payments, reviews, geolocation  
- Freely available, no license restrictions for portfolio use  
- Download: https://www.kaggle.com/datasets/olistbr/brazilian-ecommerce

---

## 2. Goals & Success Metrics

### MVP Goals (must ship)

| # | Goal |
|---|------|
| G1 | User types a natural language question → receives SQL result in < 8 seconds |
| G2 | System auto-selects correct chart type (line / bar / pie / KPI card / table) |
| G3 | "Explain this result" button generates a 2-3 sentence business insight |
| G4 | Query history is persisted and viewable |
| G5 | App runs with a single `docker compose up` command |
| G6 | All dangerous SQL (writes, drops, schema changes) is blocked |

### Stretch Goals (nice to have)

| # | Goal |
|---|------|
| S1 | Query suggestion chips (pre-built example questions) |
| S2 | Export result as CSV |
| S3 | Deployed live (Vercel + Railway) |
| S4 | Dark mode |

### Success Metrics

- < 8 second end-to-end latency for a typical query (p95)
- > 85% of natural language questions produce correct SQL on first attempt
- Zero SQL injection vulnerabilities
- Passes basic accessibility checks (keyboard navigable)

---

## 3. Tech Stack & Architecture

### Backend

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Language | Java | 21 (LTS) | Modern Java with records, sealed classes |
| Framework | Spring Boot | 3.2.x | Industry standard, CV-worthy |
| Build Tool | Maven | 3.9.x | More common in enterprise than Gradle |
| ORM / DB Access | Spring Data JPA + JDBC Template | — | JPA for entities, JDBC for dynamic query execution |
| Database | PostgreSQL | 16 | Mature, full SQL support, excellent JSON support |
| LLM Client | OpenAI Java SDK (or OkHttp) | latest | GPT-4o API calls |
| Validation | Jakarta Bean Validation | — | Input sanitization |
| API Docs | SpringDoc OpenAPI (Swagger UI) | 2.x | Auto-generated interactive docs |
| Testing | JUnit 5 + Mockito | — | Unit and integration tests |

### Frontend

| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Framework | Next.js | 14 (App Router) | App Router, RSC, file-based routing |
| Language | TypeScript | 5.x | Type safety |
| Styling | Tailwind CSS | 3.x | Utility-first, fast development |
| Charts | Recharts | 2.x | React-native, composable |
| HTTP Client | Axios | — | Interceptors, error handling |
| State Management | Zustand | — | Lightweight, no boilerplate |
| Icons | Lucide React | — | Consistent icon set |
| Animations | Framer Motion | — | Smooth transitions for result reveals |
| Table | TanStack Table (react-table) | v8 | Headless, sortable, paginated tables |

### Infrastructure

| Tool | Purpose |
|------|---------|
| Docker + Docker Compose | Local development, one-command startup |
| Railway (optional) | Backend + PostgreSQL hosting |
| Vercel (optional) | Frontend hosting |

---

## 4. System Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                         BROWSER                                  │
│                                                                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐ │
│  │  Query Input  │   │ Results Panel│   │  History Sidebar     │ │
│  │  (textarea)   │   │ Chart/Table  │   │  (past queries)      │ │
│  └──────┬───────┘   └──────▲───────┘   └──────────────────────┘ │
│         │                  │                                      │
└─────────┼──────────────────┼──────────────────────────────────────┘
          │ POST /api/query   │ JSON Response
          ▼                  │
┌─────────────────────────────────────────────────────────────────┐
│                    SPRING BOOT BACKEND                           │
│                                                                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐ │
│  │  QueryController│  │PromptBuilder │   │  SqlValidator        │ │
│  │  (REST Layer) │   │  (Schema-    │   │  (Whitelist +        │ │
│  │               │   │   Aware)     │   │   Regex Guard)       │ │
│  └──────┬───────┘   └──────┬───────┘   └──────────────────────┘ │
│         │                  │                                      │
│  ┌──────▼───────────────────▼──────────────────────────────────┐ │
│  │              QueryOrchestrationService                       │ │
│  │  1. Fetch schema metadata                                    │ │
│  │  2. Build GPT-4o prompt                                      │ │
│  │  3. Call OpenAI API                                          │ │
│  │  4. Validate + execute SQL                                   │ │
│  │  5. Infer chart type                                         │ │
│  │  6. Persist to query_history                                 │ │
│  │  7. Return structured response                               │ │
│  └──────────────────────────────────────────────────────────────┘ │
│                                                                   │
│  ┌──────────────┐   ┌──────────────┐   ┌──────────────────────┐ │
│  │ OpenAIClient  │   │ JdbcExecutor │   │ ChartTypeInferencer  │ │
│  │ (GPT-4o)      │   │ (read-only   │   │ (result shape →      │ │
│  │               │   │  datasource) │   │  chart type)         │ │
│  └──────────────┘   └──────────────┘   └──────────────────────┘ │
│                                                                   │
└─────────────────────────────────────────────────────────────────┘
          │                  │
          ▼                  ▼
┌──────────────┐    ┌─────────────────────────────────────────────┐
│  OPENAI API  │    │              POSTGRESQL                      │
│  (GPT-4o)    │    │  olist_* tables + query_history table        │
└──────────────┘    └─────────────────────────────────────────────┘
```

---

## 5. Database Schema

### Olist Tables (loaded from CSV)

```sql
-- Core tables from Olist dataset
olist_orders_dataset           -- order_id, customer_id, status, timestamps
olist_order_items_dataset      -- order_id, product_id, seller_id, price, freight_value
olist_products_dataset         -- product_id, category_name, dimensions, weight
olist_customers_dataset        -- customer_id, city, state, zip_code
olist_sellers_dataset          -- seller_id, city, state, zip_code
olist_order_payments_dataset   -- order_id, payment_type, installments, value
olist_order_reviews_dataset    -- order_id, score, comment_title, comment_message
olist_geolocation_dataset      -- zip_code, lat, lng, city, state
product_category_name_translation -- category_name_portuguese, category_name_english
```

### Application Tables (created by Spring Boot on startup)

```sql
CREATE TABLE query_history (
    id                  BIGSERIAL PRIMARY KEY,
    natural_language    TEXT NOT NULL,
    generated_sql       TEXT NOT NULL,
    chart_type          VARCHAR(50) NOT NULL,  -- 'bar', 'line', 'pie', 'kpi', 'table'
    result_row_count    INTEGER,
    execution_time_ms   INTEGER,
    error_message       TEXT,                  -- NULL if successful
    created_at          TIMESTAMP DEFAULT NOW()
);

CREATE TABLE schema_cache (
    id           BIGSERIAL PRIMARY KEY,
    table_name   VARCHAR(255) NOT NULL UNIQUE,
    schema_json  JSONB NOT NULL,               -- column names, types, sample values
    updated_at   TIMESTAMP DEFAULT NOW()
);
```

### Useful Indexes

```sql
CREATE INDEX idx_query_history_created_at ON query_history(created_at DESC);
CREATE INDEX idx_orders_status ON olist_orders_dataset(order_status);
CREATE INDEX idx_orders_purchase_date ON olist_orders_dataset(order_purchase_timestamp);
CREATE INDEX idx_order_items_product ON olist_order_items_dataset(product_id);
```

---

## 6. Backend Specification

### Package Structure

```
com.querymind
├── config
│   ├── DataSourceConfig.java          # Read-only datasource for query execution
│   ├── OpenAIConfig.java              # OpenAI client bean
│   └── SecurityConfig.java            # CORS configuration
├── controller
│   ├── QueryController.java           # POST /api/query, GET /api/query/history
│   ├── SchemaController.java          # GET /api/schema
│   └── InsightController.java         # POST /api/insight
├── service
│   ├── QueryOrchestrationService.java # Main orchestrator
│   ├── PromptBuilderService.java      # Schema-aware prompt construction
│   ├── SqlExecutionService.java       # Safe SQL execution
│   ├── SqlValidationService.java      # Security validation
│   ├── ChartTypeInferenceService.java # Result shape → chart type
│   ├── InsightGenerationService.java  # "Explain this result" GPT-4o call
│   └── SchemaIntrospectionService.java# Reads PostgreSQL metadata
├── repository
│   ├── QueryHistoryRepository.java    # JPA repository
│   └── SchemaCacheRepository.java     # JPA repository
├── model
│   ├── entity
│   │   ├── QueryHistory.java          # JPA entity
│   │   └── SchemaCache.java           # JPA entity
│   └── dto
│       ├── QueryRequest.java          # { naturalLanguage: String }
│       ├── QueryResponse.java         # Full response object
│       ├── ChartData.java             # Normalized chart-ready data
│       ├── InsightRequest.java        # { sql, results }
│       └── InsightResponse.java       # { insight: String }
└── exception
    ├── UnsafeSqlException.java
    ├── SqlExecutionException.java
    └── GlobalExceptionHandler.java    # @ControllerAdvice
```

### Key Service Logic

#### SqlValidationService.java

```java
// Blocked SQL keywords — throw UnsafeSqlException if matched
private static final List<String> BLOCKED_KEYWORDS = List.of(
    "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
    "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE",
    "--", "/*", "xp_", "pg_read_file", "COPY"
);

// Only allow SELECT statements
// Strip semicolons (prevent multiple statement execution)
// Max query length: 2000 characters
```

#### ChartTypeInferenceService.java

```java
// Decision logic:
// - 1 row, 1 numeric column → KPI_CARD
// - 1 row, multiple columns → KPI_CARD (multiple metrics)
// - Column named like "month", "date", "year", "week" + numeric → LINE_CHART
// - 1 string column + 1 numeric column, <= 20 rows → BAR_CHART
// - 1 string column + 1 numeric column, category looks like share/percent → PIE_CHART
// - Everything else → TABLE
```

#### QueryResponse.java (DTO)

```java
public record QueryResponse(
    Long historyId,
    String naturalLanguage,
    String generatedSql,
    String chartType,           // "bar" | "line" | "pie" | "kpi" | "table"
    List<Map<String, Object>> data,  // raw result rows
    ChartData chartData,        // pre-processed for Recharts
    int rowCount,
    int executionTimeMs,
    String error                // null if successful
) {}
```

### Dual DataSource Configuration

```java
// Two datasources:
// 1. PRIMARY datasource — full read/write — used by Spring Data JPA for app tables
// 2. READONLY datasource — PostgreSQL read-only role — used by SqlExecutionService
// This ensures Olist data can NEVER be accidentally mutated
```

---

## 7. Frontend Specification

### Page Structure (App Router)

```
app/
├── layout.tsx              # Root layout, global styles, fonts
├── page.tsx                # Main query interface
├── history/
│   └── page.tsx            # Full query history view
└── api/
    └── (proxy routes if needed)

components/
├── QueryInput.tsx           # Textarea + submit button + example chips
├── ResultsPanel.tsx         # Orchestrates chart/table/kpi rendering
├── charts/
│   ├── BarChartView.tsx
│   ├── LineChartView.tsx
│   ├── PieChartView.tsx
│   └── KpiCardView.tsx
├── DataTable.tsx            # TanStack Table with sort/pagination
├── SqlDisplay.tsx           # Syntax-highlighted SQL (read-only)
├── InsightBadge.tsx         # AI insight with sparkle icon
├── HistorySidebar.tsx       # Slide-in panel with past queries
├── LoadingState.tsx         # Skeleton + progress steps
└── ErrorBanner.tsx

lib/
├── api.ts                   # Axios instance + typed API functions
├── store.ts                 # Zustand store
└── utils.ts                 # formatters, date helpers
```

### Zustand Store Shape

```typescript
interface QueryStore {
  // Current query state
  query: string;
  setQuery: (q: string) => void;
  isLoading: boolean;
  currentResult: QueryResponse | null;
  error: string | null;
  
  // History
  history: QueryHistory[];
  
  // Actions
  submitQuery: () => Promise<void>;
  generateInsight: (historyId: number) => Promise<void>;
  loadHistory: () => Promise<void>;
  clearResult: () => void;
}
```

### UI Component Details

#### QueryInput
- Large textarea (4 rows), auto-expand on focus
- Submit button disabled while loading
- Below textarea: 5-6 example query chips (clickable, fills the input)
  - "What are the top 10 product categories by revenue?"
  - "Show monthly order volume for 2017"
  - "Which states have the highest average delivery delay?"
  - "What is the average review score per product category?"
  - "How many orders were placed each day last month?"
  - "Top 5 sellers by total sales value"

#### LoadingState
Show a progress stepper while loading — this is a key UX detail that makes it feel premium:
```
Step 1: ✓ Analyzing your question...
Step 2: ⟳ Generating SQL query...       ← currently active
Step 3:   Executing query...
Step 4:   Preparing visualization...
```
Use setTimeout offsets to simulate step progression even if backend is fast.

#### ResultsPanel
Layout:
```
┌─────────────────────────────────────────────────────┐
│ [Chart or Table fills this area]                    │
│                                                     │
├─────────────────────────────────────────────────────┤
│ ✨ AI Insight                              [Generate]│
│ "Sales peak in November and December, likely due    │
│  to holiday shopping patterns..."                   │
├─────────────────────────────────────────────────────┤
│ SQL Query              [Copy]  [▼ collapse]         │
│ SELECT category_name, SUM(price)...                 │
└─────────────────────────────────────────────────────┘
```

#### DataTable
- Sortable columns (click header)
- Pagination (10 rows per page)
- Row count badge ("Showing 1-10 of 127 rows")
- Export CSV button (generate client-side using json-to-csv)

### Design System

```css
/* Color Palette */
--bg-primary: #0f0f13         /* near-black background */
--bg-secondary: #1a1a24       /* card background */
--bg-tertiary: #252535        /* input/table row hover */
--accent-primary: #6366f1     /* indigo — primary CTA */
--accent-secondary: #8b5cf6   /* purple — secondary elements */
--accent-success: #10b981     /* emerald — success states */
--accent-warning: #f59e0b     /* amber — warnings */
--text-primary: #f1f5f9       /* near-white */
--text-secondary: #94a3b8     /* slate-400 */
--border: #2d2d3d             /* subtle borders */

/* Typography */
Font: Inter (body) + JetBrains Mono (SQL display)

/* Chart Colors */
['#6366f1', '#8b5cf6', '#06b6d4', '#10b981', '#f59e0b', '#ef4444']
```

---

## 8. AI & Prompt Engineering

### System Prompt (sent once per session via context)

```
You are a PostgreSQL expert. Your job is to convert natural language questions 
into valid, executable PostgreSQL SELECT queries.

Rules:
1. Only generate SELECT statements. Never use INSERT, UPDATE, DELETE, DROP, or any DDL.
2. Always use table aliases for readability.
3. Prefer readable column aliases (e.g., AS "Total Revenue" instead of AS total_revenue).
4. Limit results to 500 rows maximum unless the user explicitly asks for more.
5. Use ILIKE for string matching (case-insensitive).
6. For date operations, use PostgreSQL date functions (DATE_TRUNC, EXTRACT, etc.).
7. Return ONLY the SQL query — no explanation, no markdown, no code fences.
8. If the question cannot be answered with the available schema, return: ERROR: <reason>
```

### Dynamic User Prompt Template

```
DATABASE SCHEMA:
{schema_context}

SAMPLE DATA (first 3 rows per relevant table):
{sample_data}

QUESTION:
{natural_language_question}

Generate a PostgreSQL SELECT query to answer this question.
```

### Schema Context Construction (PromptBuilderService)

```
For each table in the database, include:
- Table name
- Column names + data types
- Sample distinct values for enum-like columns (status, payment_type, etc.)
- Foreign key relationships

Example output injected into prompt:

TABLE: olist_orders_dataset
COLUMNS:
  - order_id (varchar): unique order identifier
  - customer_id (varchar): FK → olist_customers_dataset.customer_id
  - order_status (varchar): values = ['delivered', 'shipped', 'canceled', 'processing', 'approved', 'invoiced', 'unavailable', 'created']
  - order_purchase_timestamp (timestamp)
  - order_approved_at (timestamp)
  - order_delivered_carrier_date (timestamp)
  - order_delivered_customer_date (timestamp)
  - order_estimated_delivery_date (timestamp)
```

### Insight Generation Prompt

```
System: You are a business intelligence analyst. 
Summarize SQL query results in 2-3 sentences for a non-technical business audience.
Be specific — mention actual numbers from the data. Keep it actionable.

User:
QUESTION ASKED: {natural_language}
SQL QUERY: {sql}
RESULT SUMMARY: {first_5_rows_as_json} (Total rows: {row_count})

Write a brief insight.
```

### Retry Strategy

If GPT-4o returns SQL that fails execution:
1. Capture the PostgreSQL error message
2. Re-call GPT-4o with the error appended: "This SQL failed with error: {pg_error}. Fix it."
3. Maximum 2 retries, then return error to user

---

## 9. Security & Input Validation

### SQL Injection Prevention

```
Defense Layer 1: Input length limit (max 500 chars for natural language input)
Defense Layer 2: GPT-4o system prompt explicitly forbids write operations
Defense Layer 3: SqlValidationService regex checks on generated SQL
Defense Layer 4: Read-only PostgreSQL role — even if SQL gets through, it cannot write
Defense Layer 5: No parameterized query vulnerability (we execute AI-generated SQL on read-only data, not user input directly)
```

### Read-Only PostgreSQL User Setup

```sql
-- Run this in PostgreSQL after loading data
CREATE ROLE querymind_readonly LOGIN PASSWORD 'readonly_password';
GRANT CONNECT ON DATABASE querymind TO querymind_readonly;
GRANT USAGE ON SCHEMA public TO querymind_readonly;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO querymind_readonly;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO querymind_readonly;
```

### CORS Configuration

```java
// Allow only localhost:3000 in dev, Vercel domain in prod
// Configured in SecurityConfig.java
```

### Rate Limiting (simple)

```java
// In-memory rate limiter: max 20 requests per IP per minute
// Use Bucket4j or a simple ConcurrentHashMap-based token bucket
// This prevents accidental OpenAI cost overruns
```

---

## 10. Error Handling Strategy

### Error Categories & User Messages

| Error Type | HTTP Status | User-Facing Message |
|-----------|-------------|---------------------|
| NL question too vague | 200 (soft error) | "I couldn't generate a valid SQL query for this question. Try being more specific." |
| GPT-4o returns invalid SQL | 200 (soft error) | "The generated query had errors. Please rephrase your question." |
| SQL execution timeout | 408 | "Query took too long to execute. Try a more specific question." |
| SQL execution error | 200 (soft error) | "Query execution failed: {sanitized_pg_error}" |
| Unsafe SQL detected | 400 | "This type of query is not allowed." |
| OpenAI API error | 503 | "AI service temporarily unavailable. Please try again." |
| Rate limit exceeded | 429 | "Too many requests. Please wait a moment." |

### GlobalExceptionHandler (@ControllerAdvice)

All exceptions return a consistent envelope:
```json
{
  "success": false,
  "error": {
    "code": "UNSAFE_SQL",
    "message": "Human readable message",
    "timestamp": "2026-02-27T10:00:00Z"
  }
}
```

---

## 11. Data Pipeline & Seeding

### Step 1: Download Dataset

```bash
# Option A: Kaggle CLI
kaggle datasets download -d olistbr/brazilian-ecommerce
unzip brazilian-ecommerce.zip -d data/

# Option B: Manual download from Kaggle, place CSVs in data/
```

### Step 2: Create Tables & Load Data

```sql
-- Create all 9 tables with appropriate types
-- Then load CSVs using PostgreSQL COPY command

COPY olist_orders_dataset FROM '/data/olist_orders_dataset.csv' 
  DELIMITER ',' CSV HEADER;

-- Repeat for each table
```

### Step 3: Automated Script

Create `scripts/seed.sh`:
```bash
#!/bin/bash
# Runs automatically on first docker compose up if data/ directory is mounted
# Checks if tables exist and are empty before loading
# Idempotent — safe to run multiple times
```

### Step 4: Schema Cache Population

On application startup, `SchemaIntrospectionService` runs:
```java
// Queries information_schema.columns for all olist_* tables
// Fetches 3 sample rows per table
// Builds schema_json and upserts into schema_cache table
// This JSON is used in every GPT-4o prompt
```

---

## 12. Docker & Infrastructure

### docker-compose.yml

```yaml
version: '3.9'

services:
  postgres:
    image: postgres:16-alpine
    container_name: querymind-db
    environment:
      POSTGRES_DB: querymind
      POSTGRES_USER: querymind_admin
      POSTGRES_PASSWORD: ${POSTGRES_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./data:/data:ro                        # CSV files mounted read-only
      - ./scripts/init.sql:/docker-entrypoint-initdb.d/01-init.sql
      - ./scripts/seed.sh:/docker-entrypoint-initdb.d/02-seed.sh
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U querymind_admin -d querymind"]
      interval: 10s
      timeout: 5s
      retries: 5

  backend:
    build:
      context: ./backend
      dockerfile: Dockerfile
    container_name: querymind-backend
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/querymind
      SPRING_DATASOURCE_USERNAME: querymind_admin
      SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD}
      READONLY_DATASOURCE_USERNAME: querymind_readonly
      READONLY_DATASOURCE_PASSWORD: ${READONLY_PASSWORD}
      OPENAI_API_KEY: ${OPENAI_API_KEY}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy

  frontend:
    build:
      context: ./frontend
      dockerfile: Dockerfile
    container_name: querymind-frontend
    environment:
      NEXT_PUBLIC_API_URL: http://localhost:8080
    ports:
      - "3000:3000"
    depends_on:
      - backend

volumes:
  postgres_data:
```

### Backend Dockerfile

```dockerfile
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Frontend Dockerfile

```dockerfile
FROM node:20-alpine AS build
WORKDIR /app
COPY package*.json .
RUN npm ci
COPY . .
RUN npm run build

FROM node:20-alpine
WORKDIR /app
COPY --from=build /app/.next/standalone .
COPY --from=build /app/.next/static ./.next/static
EXPOSE 3000
CMD ["node", "server.js"]
```

---

## 13. Testing Strategy

### Backend Unit Tests

```
SqlValidationServiceTest.java
  - testBlocksInsertStatement()
  - testBlocksDropTable()
  - testAllowsSelectStatement()
  - testBlocksSqlInjectionAttempt()

ChartTypeInferenceServiceTest.java
  - testSingleNumericRowReturnsKpi()
  - testDatePlusNumericReturnsLine()
  - testStringPlusNumericReturnsBar()
  - testManyColumnsReturnsTable()

PromptBuilderServiceTest.java
  - testPromptContainsSchemaInfo()
  - testPromptContainsSampleData()
  - testPromptLengthUnderLimit()
```

### Backend Integration Tests

```
QueryControllerIntegrationTest.java
  - testSubmitQueryReturnsResult()         (with TestContainers PostgreSQL)
  - testUnsafeSqlIsRejected()
  - testQueryHistoryIsPersisted()
```

### Frontend Tests

```
# Snapshot tests for chart components
# API mock tests for QueryInput submission
# (Vitest + React Testing Library)
```

---

## 14. Day-by-Day Development Plan

### Day 1 — Backend Foundation

**Morning**
- [ ] Create Spring Boot project (Spring Initializr): Web, JPA, JDBC, Validation, Actuator, PostgreSQL Driver, SpringDoc OpenAPI
- [ ] Set up Docker Compose with PostgreSQL
- [ ] Download Olist dataset, create init.sql with table definitions
- [ ] Write seed.sh to load CSVs on first startup
- [ ] Verify data loaded: `SELECT COUNT(*) FROM olist_orders_dataset;` → should be ~99k

**Afternoon**
- [ ] Create `QueryHistory` JPA entity + repository
- [ ] Create `SchemaCache` JPA entity + repository
- [ ] Implement `SchemaIntrospectionService` (reads information_schema)
- [ ] Create `/api/schema` endpoint, verify it returns table metadata
- [ ] Set up dual DataSource (admin + readonly)

**End of Day Check:**
- Docker Compose starts cleanly ✓
- Data loaded in PostgreSQL ✓
- `/api/schema` returns JSON ✓

---

### Day 2 — NL-to-SQL Core

**Morning**
- [ ] Implement `OpenAIClient` (HTTP call to GPT-4o with system prompt)
- [ ] Implement `SqlValidationService` (keyword blocklist + regex)
- [ ] Implement `SqlExecutionService` (executes on readonly datasource, returns List<Map<String, Object>>)
- [ ] Write unit tests for SqlValidationService

**Afternoon**
- [ ] Implement `PromptBuilderService` (fetches schema cache, builds full prompt)
- [ ] Implement `QueryOrchestrationService` (ties everything together)
- [ ] Create `QueryController` with `POST /api/query`
- [ ] Test with curl: `curl -X POST localhost:8080/api/query -d '{"naturalLanguage":"How many orders were placed in 2017?"}'`
- [ ] Add retry logic (max 2 retries on SQL execution failure)
- [ ] Persist query to `query_history` table

**End of Day Check:**
- Natural language question → correct SQL → correct result ✓
- Unsafe SQL blocked ✓
- History persisted ✓

---

### Day 3 — Intelligence Layer

**Morning**
- [ ] Implement `ChartTypeInferenceService`
- [ ] Implement `InsightGenerationService` (second GPT-4o call)
- [ ] Add `POST /api/insight` endpoint
- [ ] Add `GET /api/query/history` endpoint (paginated, last 50)
- [ ] Enrich `QueryResponse` DTO with chartType + chartData (pre-processed for Recharts)

**Afternoon**
- [ ] Add simple in-memory rate limiter
- [ ] Implement `GlobalExceptionHandler`
- [ ] Add query execution timeout (30 seconds max)
- [ ] Write integration tests (TestContainers)
- [ ] Verify Swagger UI at localhost:8080/swagger-ui.html

**End of Day Check:**
- Chart type correctly inferred for 5 test queries ✓
- Insight generation working ✓
- All error cases handled gracefully ✓

---

### Day 4 — Frontend

**Morning**
- [ ] Create Next.js 14 project with TypeScript + Tailwind
- [ ] Set up design tokens (colors, typography) in tailwind.config.ts
- [ ] Implement Zustand store
- [ ] Implement `lib/api.ts` with typed API functions
- [ ] Build `QueryInput` component with example chips

**Afternoon**
- [ ] Build `LoadingState` component with step stepper
- [ ] Build `BarChartView`, `LineChartView`, `PieChartView` with Recharts
- [ ] Build `KpiCardView`
- [ ] Build `DataTable` with TanStack Table (sort + pagination)
- [ ] Build `SqlDisplay` with syntax highlighting
- [ ] Wire up `ResultsPanel` to dynamically render correct component
- [ ] Test all 6 chart types with mock data

**End of Day Check:**
- End-to-end: type question → see chart ✓
- All chart types render correctly ✓

---

### Day 5 — Polish & Deploy

**Morning**
- [ ] Build `HistorySidebar` (slide-in, click to re-run past queries)
- [ ] Build `InsightBadge` with generate button
- [ ] Add Framer Motion animations (result reveal, loading fade)
- [ ] Export CSV functionality on DataTable
- [ ] Error states for all failure scenarios
- [ ] Responsive design check (mobile-friendly)

**Afternoon**
- [ ] Write comprehensive README.md (setup guide, screenshots, example queries)
- [ ] Add Frontend Dockerfile, update docker-compose.yml
- [ ] Test full `docker compose up` from scratch
- [ ] Take screenshots / record demo GIF for README
- [ ] Push to GitHub with clean commit history
- [ ] (Optional) Deploy: Railway (backend + DB) + Vercel (frontend)
- [ ] Update CV bullet

**End of Day Check:**
- `docker compose up` → app running ✓
- README with setup instructions ✓
- GitHub repo public ✓

---

## 15. File & Folder Structure

```
querymind/
├── docker-compose.yml
├── .env.example
├── README.md
├── data/                              # Olist CSVs (gitignored)
│   ├── olist_orders_dataset.csv
│   └── ...
├── scripts/
│   ├── init.sql                       # Table creation
│   └── seed.sh                        # CSV loading
├── backend/
│   ├── Dockerfile
│   ├── pom.xml
│   └── src/
│       ├── main/java/com/querymind/
│       │   ├── config/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   ├── model/
│       │   │   ├── entity/
│       │   │   └── dto/
│       │   └── exception/
│       ├── main/resources/
│       │   └── application.yml
│       └── test/java/com/querymind/
└── frontend/
    ├── Dockerfile
    ├── package.json
    ├── tailwind.config.ts
    ├── tsconfig.json
    └── src/
        ├── app/
        ├── components/
        └── lib/
```

---

## 16. Environment Variables

### .env.example

```bash
# PostgreSQL
POSTGRES_PASSWORD=your_strong_password_here
READONLY_PASSWORD=readonly_password_here

# OpenAI
OPENAI_API_KEY=sk-...

# App Config
SPRING_PROFILES_ACTIVE=dev
MAX_QUERY_EXECUTION_TIME_SECONDS=30
MAX_RESULT_ROWS=500
RATE_LIMIT_REQUESTS_PER_MINUTE=20

# Frontend
NEXT_PUBLIC_API_URL=http://localhost:8080
```

### application.yml

```yaml
spring:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${SPRING_DATASOURCE_USERNAME}
    password: ${SPRING_DATASOURCE_PASSWORD}
  jpa:
    hibernate:
      ddl-auto: update
    show-sql: false

readonly:
  datasource:
    url: ${SPRING_DATASOURCE_URL}
    username: ${READONLY_DATASOURCE_USERNAME}
    password: ${READONLY_DATASOURCE_PASSWORD}

openai:
  api-key: ${OPENAI_API_KEY}
  model: gpt-4o
  max-tokens: 1000
  temperature: 0.1

querymind:
  max-execution-time-seconds: ${MAX_QUERY_EXECUTION_TIME_SECONDS:30}
  max-result-rows: ${MAX_RESULT_ROWS:500}
  rate-limit: ${RATE_LIMIT_REQUESTS_PER_MINUTE:20}

springdoc:
  swagger-ui:
    path: /swagger-ui.html
```

---

## 17. API Reference

### POST /api/query

**Request:**
```json
{
  "naturalLanguage": "What are the top 5 product categories by total revenue?"
}
```

**Response (success):**
```json
{
  "success": true,
  "data": {
    "historyId": 42,
    "naturalLanguage": "What are the top 5 product categories by total revenue?",
    "generatedSql": "SELECT t.category_name_english AS \"Category\", SUM(i.price) AS \"Total Revenue\" FROM olist_order_items_dataset i JOIN olist_products_dataset p ON i.product_id = p.product_id JOIN product_category_name_translation t ON p.product_category_name = t.category_name_portuguese GROUP BY t.category_name_english ORDER BY SUM(i.price) DESC LIMIT 5",
    "chartType": "bar",
    "rowCount": 5,
    "executionTimeMs": 143,
    "data": [
      {"Category": "bed_bath_table", "Total Revenue": 1711682.38},
      ...
    ],
    "chartData": {
      "xKey": "Category",
      "yKey": "Total Revenue",
      "rows": [...]
    }
  }
}
```

**Response (error):**
```json
{
  "success": false,
  "error": {
    "code": "SQL_EXECUTION_ERROR",
    "message": "Could not generate a valid query for this question. Try rephrasing.",
    "timestamp": "2026-02-27T10:00:00Z"
  }
}
```

---

### POST /api/insight

**Request:**
```json
{
  "historyId": 42
}
```

**Response:**
```json
{
  "insight": "Bed, bath and table products dominate marketplace revenue at 1.7M BRL, nearly double the second category (health & beauty at 870K BRL). This suggests the Olist platform has a particularly strong home goods customer base worth targeting for promotions."
}
```

---

### GET /api/query/history?page=0&size=20

**Response:**
```json
{
  "content": [
    {
      "id": 42,
      "naturalLanguage": "Top 5 categories by revenue",
      "chartType": "bar",
      "rowCount": 5,
      "executionTimeMs": 143,
      "createdAt": "2026-02-27T10:00:00Z"
    }
  ],
  "totalElements": 87,
  "totalPages": 5
}
```

---

### GET /api/schema

**Response:**
```json
{
  "tables": [
    {
      "tableName": "olist_orders_dataset",
      "columns": [
        { "name": "order_id", "type": "character varying" },
        { "name": "order_status", "type": "character varying", "sampleValues": ["delivered", "shipped", "canceled"] }
      ],
      "rowCount": 99441
    }
  ]
}
```

---

## 18. CV & Portfolio Positioning

### CV Bullet (after completion)

```
Built QueryMind, a schema-aware NL-to-SQL analytics engine (Spring Boot, PostgreSQL, 
GPT-4o) with auto chart type inference and AI-generated insights, enabling natural 
language querying of a 100K+ row e-commerce dataset with < 8s end-to-end latency
```

### GitHub README Must-Haves

- [ ] Demo GIF at the top (record with LICEcap or Kap)
- [ ] 3-4 screenshots showing different chart types
- [ ] "How it works" section with architecture diagram
- [ ] Quick start instructions (3 commands max)
- [ ] List of 10 example questions users can try
- [ ] Tech stack badges

### What to Say in Interviews

**For AI Engineer roles:**
> "I built a schema-aware prompting system where every GPT-4o call receives dynamic context — table definitions, column types, and sample enum values. This dramatically reduces hallucination because the model knows exactly what values are valid. I also implemented a retry loop that feeds PostgreSQL error messages back to GPT-4o for self-correction."

**For BI Engineer / Data roles:**
> "I worked with a 100K row relational dataset across 9 tables, wrote complex joins and aggregations in SQL, and built an inference system that automatically selects the right visualization based on the result shape — time series → line chart, categorical + numeric → bar chart, single value → KPI card."

**For Java/Backend roles:**
> "I implemented a dual DataSource pattern where the application uses a privileged connection for writes to app tables, but all user-generated SQL runs through a read-only PostgreSQL role. Even if the SQL validation layer is bypassed, the database role makes writes physically impossible."

---

## 19. Future Enhancements (Post-MVP)

| Feature | Complexity | Impact |
|---------|-----------|--------|
| Multi-turn conversation (chat history for SQL refinement) | Medium | High |
| Saved dashboards (pin queries, build a personal BI dashboard) | High | High |
| Dataset upload (user uploads their own CSV) | Medium | Very High |
| Query optimizer suggestions (GPT-4o reviews slow queries) | Low | Medium |
| Scheduled reports via email | Medium | Medium |
| Multi-database support (MySQL, SQLite) | Medium | Low |
| Fine-tuned model on SQL generation (replace GPT-4o) | Very High | Medium |

---

*This document is the single source of truth for QueryMind development. Every implementation decision should be traceable to a requirement here.*