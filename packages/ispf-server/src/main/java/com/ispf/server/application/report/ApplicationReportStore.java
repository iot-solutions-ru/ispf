package com.ispf.server.application.report;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ApplicationReportStore {

    private final JdbcTemplate jdbcTemplate;
    private final String reportsTable;

    public ApplicationReportStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.reportsTable = platformSqlCatalog.table("application_reports");
    }

    public void upsert(DeployedReport report) {
        Optional<DeployedReport> existing = find(report.appId(), report.reportId());
        if (existing.isPresent()) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET title = ?, description = ?, query_sql = ?, parameters_json = ?,
                        columns_json = ?, max_rows = ?, deployed_at = ?
                    WHERE app_id = ? AND report_id = ?
                    """.formatted(reportsTable),
                    report.title(),
                    report.description(),
                    report.querySql(),
                    report.parametersJson(),
                    report.columnsJson(),
                    report.maxRows(),
                    Timestamp.from(Instant.now()),
                    report.appId(),
                    report.reportId()
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (
                    id, app_id, report_id, title, description, query_sql,
                    parameters_json, columns_json, max_rows, deployed_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.formatted(reportsTable),
                report.id(),
                report.appId(),
                report.reportId(),
                report.title(),
                report.description(),
                report.querySql(),
                report.parametersJson(),
                report.columnsJson(),
                report.maxRows(),
                Timestamp.from(Instant.now())
        );
    }

    public List<DeployedReport> listByApp(String appId) {
        return jdbcTemplate.query("""
                SELECT id, app_id, report_id, title, description, query_sql,
                       parameters_json, columns_json, max_rows
                FROM %s
                WHERE app_id = ?
                ORDER BY report_id
                """.formatted(reportsTable),
                (rs, rowNum) -> new DeployedReport(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("app_id"),
                        rs.getString("report_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("query_sql"),
                        rs.getString("parameters_json"),
                        rs.getString("columns_json"),
                        rs.getInt("max_rows")
                ),
                appId
        );
    }

    public Optional<DeployedReport> find(String appId, String reportId) {
        List<DeployedReport> rows = jdbcTemplate.query("""
                SELECT id, app_id, report_id, title, description, query_sql,
                       parameters_json, columns_json, max_rows
                FROM %s
                WHERE app_id = ? AND report_id = ?
                """.formatted(reportsTable),
                (rs, rowNum) -> new DeployedReport(
                        UUID.fromString(rs.getString("id")),
                        rs.getString("app_id"),
                        rs.getString("report_id"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("query_sql"),
                        rs.getString("parameters_json"),
                        rs.getString("columns_json"),
                        rs.getInt("max_rows")
                ),
                appId,
                reportId
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public record DeployedReport(
            UUID id,
            String appId,
            String reportId,
            String title,
            String description,
            String querySql,
            String parametersJson,
            String columnsJson,
            int maxRows
    ) {
    }
}
