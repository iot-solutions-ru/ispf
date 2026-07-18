package com.ispf.server.ai.agent;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class AgentSessionDocumentStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public AgentSessionDocumentStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = platformSqlCatalog.table("agent_session_documents");
    }

    public void insert(AgentSessionDocumentRecord record) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    doc_id, session_id, filename, mime_type, description, content_text,
                    byte_size, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table),
                record.docId(),
                record.sessionId(),
                record.filename(),
                record.mimeType(),
                record.description(),
                record.contentText(),
                record.byteSize(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
        );
    }

    public Optional<AgentSessionDocumentRecord> findById(String sessionId, String docId) {
        List<AgentSessionDocumentRecord> rows = jdbcTemplate.query("""
                SELECT doc_id, session_id, filename, mime_type, description, content_text,
                       byte_size, created_at, updated_at
                FROM %s WHERE session_id = ? AND doc_id = ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                sessionId,
                docId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<AgentSessionDocumentRecord> listForSession(String sessionId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.query("""
                SELECT doc_id, session_id, filename, mime_type, description, content_text,
                       byte_size, created_at, updated_at
                FROM %s
                WHERE session_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                sessionId,
                capped
        );
    }

    public List<AgentSessionDocumentRecord> search(String sessionId, String query, int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + query.trim().toLowerCase() + "%";
        return jdbcTemplate.query("""
                SELECT doc_id, session_id, filename, mime_type, description, content_text,
                       byte_size, created_at, updated_at
                FROM %s
                WHERE session_id = ?
                  AND (
                    LOWER(filename) LIKE ?
                    OR LOWER(COALESCE(description, '')) LIKE ?
                    OR LOWER(content_text) LIKE ?
                  )
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                sessionId,
                pattern,
                pattern,
                pattern,
                capped
        );
    }

    public int countForSession(String sessionId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE session_id = ?".formatted(table),
                Integer.class,
                sessionId
        );
        return count != null ? count : 0;
    }

    public void delete(String sessionId, String docId) {
        jdbcTemplate.update("DELETE FROM %s WHERE session_id = ? AND doc_id = ?".formatted(table), sessionId, docId);
    }

    public static String newDocId() {
        return UUID.randomUUID().toString();
    }

    private static AgentSessionDocumentRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AgentSessionDocumentRecord(
                rs.getString("doc_id"),
                rs.getString("session_id"),
                rs.getString("filename"),
                rs.getString("mime_type"),
                rs.getString("description"),
                rs.getString("content_text"),
                rs.getLong("byte_size"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
        );
    }
}
