package com.ispf.server.operator;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class OperatorAppDocumentStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public OperatorAppDocumentStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = platformSqlCatalog.table("operator_app_documents");
    }

    public void insert(OperatorAppDocumentRecord record) {
        jdbcTemplate.update("""
                INSERT INTO %s (
                    doc_id, app_id, filename, mime_type, description, content_text,
                    byte_size, created_at, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(table),
                record.docId(),
                record.appId(),
                record.filename(),
                record.mimeType(),
                record.description(),
                record.contentText(),
                record.byteSize(),
                Timestamp.from(record.createdAt()),
                Timestamp.from(record.updatedAt())
        );
    }

    public void update(OperatorAppDocumentRecord record) {
        jdbcTemplate.update("""
                UPDATE %s
                SET filename = ?, mime_type = ?, description = ?, content_text = ?,
                    byte_size = ?, updated_at = ?
                WHERE doc_id = ?
                """.formatted(table),
                record.filename(),
                record.mimeType(),
                record.description(),
                record.contentText(),
                record.byteSize(),
                Timestamp.from(record.updatedAt()),
                record.docId()
        );
    }

    public Optional<OperatorAppDocumentRecord> findById(String appId, String docId) {
        List<OperatorAppDocumentRecord> rows = jdbcTemplate.query("""
                SELECT doc_id, app_id, filename, mime_type, description, content_text,
                       byte_size, created_at, updated_at
                FROM %s WHERE app_id = ? AND doc_id = ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                appId,
                docId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public List<OperatorAppDocumentRecord> listForApp(String appId, int limit) {
        int capped = Math.min(Math.max(limit, 1), 200);
        return jdbcTemplate.query("""
                SELECT doc_id, app_id, filename, mime_type, description, content_text,
                       byte_size, created_at, updated_at
                FROM %s
                WHERE app_id = ?
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                appId,
                capped
        );
    }

    public List<OperatorAppDocumentRecord> search(String appId, String query, int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        String pattern = "%" + query.trim().toLowerCase() + "%";
        return jdbcTemplate.query("""
                SELECT doc_id, app_id, filename, mime_type, description, content_text,
                       byte_size, created_at, updated_at
                FROM %s
                WHERE app_id = ?
                  AND (
                    LOWER(filename) LIKE ?
                    OR LOWER(COALESCE(description, '')) LIKE ?
                    OR LOWER(content_text) LIKE ?
                  )
                ORDER BY updated_at DESC
                LIMIT ?
                """.formatted(table),
                (rs, rowNum) -> mapRow(rs),
                appId,
                pattern,
                pattern,
                pattern,
                capped
        );
    }

    public int countForApp(String appId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE app_id = ?".formatted(table),
                Integer.class,
                appId
        );
        return count != null ? count : 0;
    }

    public void delete(String appId, String docId) {
        jdbcTemplate.update("DELETE FROM %s WHERE app_id = ? AND doc_id = ?".formatted(table), appId, docId);
    }

    public static String newDocId() {
        return UUID.randomUUID().toString();
    }

    private static OperatorAppDocumentRecord mapRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new OperatorAppDocumentRecord(
                rs.getString("doc_id"),
                rs.getString("app_id"),
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
