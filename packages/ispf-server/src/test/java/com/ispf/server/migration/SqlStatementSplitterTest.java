package com.ispf.server.migration;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqlStatementSplitterTest {

    @Test
    void splitsSingleLineMultiStatementBatch() {
        List<String> statements = SqlStatementSplitter.split(
                "CREATE TABLE t (id INT); INSERT INTO t VALUES (1); SELECT 1"
        );
        assertEquals(3, statements.size());
        assertEquals("CREATE TABLE t (id INT)", statements.get(0));
        assertEquals("INSERT INTO t VALUES (1)", statements.get(1));
        assertEquals("SELECT 1", statements.get(2));
    }

    @Test
    void splitsMultilineStatements() {
        List<String> statements = SqlStatementSplitter.split("""
                CREATE TABLE t (id INT);
                INSERT INTO t VALUES (1);
                """);
        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE t (id INT)", statements.get(0));
        assertEquals("INSERT INTO t VALUES (1)", statements.get(1));
    }

    @Test
    void ignoresSemicolonInsideSingleQuotedLiteral() {
        List<String> statements = SqlStatementSplitter.split(
                "INSERT INTO t VALUES ('a;b'); SELECT 1"
        );
        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ('a;b')", statements.get(0));
        assertEquals("SELECT 1", statements.get(1));
    }

    @Test
    void handlesEscapedSingleQuoteInLiteral() {
        List<String> statements = SqlStatementSplitter.split(
                "INSERT INTO t VALUES ('it''s fine'); UPDATE t SET x = 1"
        );
        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ('it''s fine')", statements.get(0));
        assertEquals("UPDATE t SET x = 1", statements.get(1));
    }
}
