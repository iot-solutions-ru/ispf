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
    void keepsSemicolonInsideDollarQuote() {
        List<String> statements = SqlStatementSplitter.split(
                "INSERT INTO t VALUES ($$a;b$$); SELECT 1"
        );
        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ($$a;b$$)", statements.get(0));
        assertEquals("SELECT 1", statements.get(1));
    }

    @Test
    void keepsFunctionBodyInsideTaggedDollarQuote() {
        List<String> statements = SqlStatementSplitter.split("""
                CREATE FUNCTION reserve_order() RETURNS void AS $body$
                BEGIN
                  PERFORM 1;
                END;
                $body$ LANGUAGE plpgsql;
                SELECT 1
                """);
        assertEquals(2, statements.size());
        assertEquals(true, statements.get(0).contains("PERFORM 1;"));
        assertEquals(true, statements.get(0).contains("$body$ LANGUAGE plpgsql"));
        assertEquals("SELECT 1", statements.get(1));
    }

    @Test
    void dollarQuoteDoesNotStartInsideSingleQuotes() {
        List<String> statements = SqlStatementSplitter.split(
                "INSERT INTO t VALUES ('$$a;b$$'); SELECT 1"
        );
        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ('$$a;b$$')", statements.get(0));
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

    @Test
    void ignoresSemicolonInsideLineComment() {
        List<String> statements = SqlStatementSplitter.split(
                "CREATE TABLE t(id int); -- seed: a; b\nINSERT INTO t VALUES (1);"
        );
        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE t(id int)", statements.get(0));
        assertEquals(true, statements.get(1).contains("INSERT INTO t VALUES (1)"));
        assertEquals(true, statements.get(1).contains("-- seed: a; b"));
    }

    @Test
    void ignoresSemicolonInsideBlockComment() {
        List<String> statements = SqlStatementSplitter.split(
                "CREATE TABLE t(id int); /* note: a; b */ INSERT INTO t VALUES (1);"
        );
        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE t(id int)", statements.get(0));
        assertEquals(true, statements.get(1).contains("INSERT INTO t VALUES (1)"));
        assertEquals(true, statements.get(1).contains("/* note: a; b */"));
    }

    @Test
    void apostropheInsideLineCommentDoesNotToggleLiteralState() {
        List<String> statements = SqlStatementSplitter.split(
                "CREATE TABLE t(id int); -- don't break\nINSERT INTO t VALUES (1); SELECT 1"
        );
        assertEquals(3, statements.size());
        assertEquals("CREATE TABLE t(id int)", statements.get(0));
        assertEquals(true, statements.get(1).contains("INSERT INTO t VALUES (1)"));
        assertEquals("SELECT 1", statements.get(2));
    }

    @Test
    void apostropheInsideBlockCommentDoesNotToggleLiteralState() {
        List<String> statements = SqlStatementSplitter.split(
                "CREATE TABLE t(id int); /* don't break */ INSERT INTO t VALUES (1);"
        );
        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE t(id int)", statements.get(0));
        assertEquals(true, statements.get(1).contains("INSERT INTO t VALUES (1)"));
    }
}
