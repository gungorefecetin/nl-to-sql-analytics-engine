package com.querymind.service;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.querymind.exception.UnsafeSqlException;

@Service
public class SqlValidationService {

    private static final int MAX_SQL_LENGTH = 2000;

    private static final List<String> BLOCKED_KEYWORDS = List.of(
            "INSERT", "UPDATE", "DELETE", "DROP", "CREATE", "ALTER",
            "TRUNCATE", "GRANT", "REVOKE", "EXEC", "EXECUTE",
            "--", "/*", "xp_", "pg_read_file", "COPY"
    );

    // Word-boundary patterns for SQL keywords (avoid false positives like "execution_time")
    // Non-word tokens (-- /* xp_ pg_read_file) use plain contains matching
    private static final List<Pattern> KEYWORD_PATTERNS;

    static {
        KEYWORD_PATTERNS = BLOCKED_KEYWORDS.stream()
                .map(kw -> {
                    if (kw.matches("[A-Z]+")) {
                        // SQL keyword — match as whole word, case-insensitive
                        return Pattern.compile("\\b" + kw + "\\b", Pattern.CASE_INSENSITIVE);
                    } else {
                        // Symbol/function token — match literally, case-insensitive
                        return Pattern.compile(Pattern.quote(kw), Pattern.CASE_INSENSITIVE);
                    }
                })
                .toList();
    }

    /**
     * Validates and sanitizes AI-generated SQL.
     * Returns the cleaned SQL (trimmed, semicolons stripped) if safe.
     *
     * @throws UnsafeSqlException if any validation rule is violated
     */
    public String validate(String sql) {
        if (sql == null || sql.isBlank()) {
            throw new UnsafeSqlException("SQL is empty");
        }

        String cleaned = sql.strip();

        // Strip trailing semicolons to prevent statement chaining
        while (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).stripTrailing();
        }

        if (cleaned.isEmpty()) {
            throw new UnsafeSqlException("SQL is empty after sanitization");
        }

        // Length check
        if (cleaned.length() > MAX_SQL_LENGTH) {
            throw new UnsafeSqlException("SQL exceeds maximum length of " + MAX_SQL_LENGTH + " characters");
        }

        // SELECT-only enforcement
        if (!cleaned.toUpperCase().startsWith("SELECT")) {
            throw new UnsafeSqlException("Only SELECT statements are allowed");
        }

        // Blocked keyword check
        for (int i = 0; i < BLOCKED_KEYWORDS.size(); i++) {
            if (KEYWORD_PATTERNS.get(i).matcher(cleaned).find()) {
                throw new UnsafeSqlException("Blocked keyword detected: " + BLOCKED_KEYWORDS.get(i));
            }
        }

        return cleaned;
    }
}
