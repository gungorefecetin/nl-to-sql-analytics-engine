package com.querymind.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.querymind.exception.UnsafeSqlException;

class SqlValidationServiceTest {

    private SqlValidationService service;

    @BeforeEach
    void setUp() {
        service = new SqlValidationService();
    }

    // ── Valid queries pass ──

    @Test
    void validSelectPasses() {
        String result = service.validate("SELECT COUNT(*) FROM olist_orders_dataset");
        assertThat(result).isEqualTo("SELECT COUNT(*) FROM olist_orders_dataset");
    }

    @Test
    void validSelectWithJoinPasses() {
        String sql = "SELECT o.order_id, c.customer_city "
                + "FROM olist_orders_dataset o "
                + "JOIN olist_customers_dataset c ON o.customer_id = c.customer_id "
                + "LIMIT 10";
        assertThat(service.validate(sql)).isEqualTo(sql);
    }

    @Test
    void lowercaseSelectPasses() {
        String result = service.validate("select 1");
        assertThat(result).isEqualTo("select 1");
    }

    @Test
    void mixedCaseSelectPasses() {
        String result = service.validate("SeLeCt * FROM orders");
        assertThat(result).isEqualTo("SeLeCt * FROM orders");
    }

    // ── Semicolon stripping ──

    @Test
    void trailingSemicolonIsStripped() {
        String result = service.validate("SELECT 1;");
        assertThat(result).isEqualTo("SELECT 1");
    }

    @Test
    void multipleSemicolonsStripped() {
        String result = service.validate("SELECT 1;;;");
        assertThat(result).isEqualTo("SELECT 1");
    }

    @Test
    void semicolonWithTrailingSpaceStripped() {
        String result = service.validate("SELECT 1 ;  ");
        assertThat(result).isEqualTo("SELECT 1");
    }

    // ── Blocked keywords ──

    @ParameterizedTest
    @ValueSource(strings = {
            "INSERT INTO orders VALUES (1)",
            "UPDATE orders SET status = 'x'",
            "DELETE FROM orders",
            "DROP TABLE orders",
            "CREATE TABLE evil (id INT)",
            "ALTER TABLE orders ADD col INT",
            "TRUNCATE orders",
            "GRANT ALL ON orders TO evil",
            "REVOKE SELECT ON orders FROM readonly"
    })
    void blockedDmlDdlKeywordsRejected(String sql) {
        assertThatThrownBy(() -> service.validate(sql))
                .isInstanceOf(UnsafeSqlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM orders; DROP TABLE orders",
            "SELECT * FROM orders; DELETE FROM orders"
    })
    void statementChainingWithSemicolonBlocked(String sql) {
        // After semicolon stripping, these still contain blocked keywords
        assertThatThrownBy(() -> service.validate(sql))
                .isInstanceOf(UnsafeSqlException.class);
    }

    @Test
    void execBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT EXEC('some_proc')"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("EXEC");
    }

    @Test
    void executeBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT EXECUTE('some_proc')"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("EXECUTE");
    }

    // ── Comment injection ──

    @Test
    void singleLineCommentBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT 1 -- comment"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("--");
    }

    @Test
    void blockCommentBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT 1 /* comment */"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("/*");
    }

    // ── Dangerous functions ──

    @Test
    void xpPrefixBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT xp_cmdshell('whoami')"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("xp_");
    }

    @Test
    void pgReadFileBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT pg_read_file('/etc/passwd')"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("pg_read_file");
    }

    @Test
    void copyBlocked() {
        assertThatThrownBy(() -> service.validate("SELECT * FROM COPY"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("COPY");
    }

    // ── Case-insensitive bypass attempts ──

    @ParameterizedTest
    @ValueSource(strings = {
            "SELECT * FROM orders; drop table orders",
            "SELECT * FROM orders; DeLeTe FROM orders",
            "SELECT * FROM orders; Insert INTO orders VALUES (1)"
    })
    void lowercaseMixedCaseBypassBlocked(String sql) {
        assertThatThrownBy(() -> service.validate(sql))
                .isInstanceOf(UnsafeSqlException.class);
    }

    // ── SELECT-only enforcement ──

    @Test
    void nonSelectStatementRejected() {
        assertThatThrownBy(() -> service.validate("WITH cte AS (SELECT 1) DELETE FROM orders"))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("Only SELECT");
    }

    // ── Length overflow ──

    @Test
    void maxLengthExceededRejected() {
        String longSql = "SELECT " + "x".repeat(2000);
        assertThatThrownBy(() -> service.validate(longSql))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("maximum length");
    }

    @Test
    void exactlyMaxLengthPasses() {
        // 7 chars "SELECT " + 1993 chars = 2000 total
        String sql = "SELECT " + "x".repeat(1993);
        assertThat(service.validate(sql)).hasSize(2000);
    }

    // ── Null / blank ──

    @Test
    void nullRejected() {
        assertThatThrownBy(() -> service.validate(null))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void blankRejected() {
        assertThatThrownBy(() -> service.validate("   "))
                .isInstanceOf(UnsafeSqlException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void onlySemicolonsRejected() {
        assertThatThrownBy(() -> service.validate(";;;"))
                .isInstanceOf(UnsafeSqlException.class);
    }

    // ── No false positives on substrings ──

    @Test
    void columnNameContainingKeywordSubstringPasses() {
        // "execution_time" contains "EXEC" and "EXECUTE" as substrings but shouldn't trigger
        String sql = "SELECT execution_time_ms FROM query_history";
        assertThat(service.validate(sql)).isEqualTo(sql);
    }

    @Test
    void columnNameWithUpdateSubstringPasses() {
        // "updated_at" contains "UPDATE" as substring
        String sql = "SELECT updated_at FROM schema_cache";
        assertThat(service.validate(sql)).isEqualTo(sql);
    }

    @Test
    void columnNameWithDeletedPasses() {
        String sql = "SELECT is_deleted FROM some_table";
        assertThat(service.validate(sql)).isEqualTo(sql);
    }

    @Test
    void aliasWithCreateSubstringPasses() {
        String sql = "SELECT created_at FROM olist_orders_dataset";
        assertThat(service.validate(sql)).isEqualTo(sql);
    }
}
