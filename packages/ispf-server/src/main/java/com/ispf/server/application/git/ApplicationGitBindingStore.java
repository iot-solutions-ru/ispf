package com.ispf.server.application.git;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Repository
public class ApplicationGitBindingStore {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public ApplicationGitBindingStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = platformSqlCatalog.table("application_git_bindings");
    }

    public Optional<Map<String, Object>> find(String appId) {
        return jdbcTemplate.query(
                """
                        SELECT app_id, provider, remote_url, default_branch, bundle_path,
                               credential_object_path, last_synced_sha, last_synced_version,
                               last_synced_at, updated_at
                        FROM %s WHERE app_id = ?
                        """.formatted(table),
                rs -> {
                    if (!rs.next()) {
                        return Optional.empty();
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("appId", rs.getString("app_id"));
                    row.put("provider", rs.getString("provider"));
                    row.put("remoteUrl", rs.getString("remote_url"));
                    row.put("defaultBranch", rs.getString("default_branch"));
                    row.put("bundlePath", rs.getString("bundle_path"));
                    row.put("credentialObjectPath", rs.getString("credential_object_path"));
                    row.put("lastSyncedSha", rs.getString("last_synced_sha"));
                    row.put("lastSyncedVersion", rs.getString("last_synced_version"));
                    Timestamp syncedAt = rs.getTimestamp("last_synced_at");
                    row.put("lastSyncedAt", syncedAt != null ? syncedAt.toInstant().toString() : null);
                    Timestamp updatedAt = rs.getTimestamp("updated_at");
                    row.put("updatedAt", updatedAt != null ? updatedAt.toInstant().toString() : null);
                    return Optional.of(row);
                },
                appId
        );
    }

    public void upsert(
            String appId,
            String provider,
            String remoteUrl,
            String defaultBranch,
            String bundlePath,
            String credentialObjectPath
    ) {
        Instant now = Instant.now();
        int updated = jdbcTemplate.update(
                """
                        UPDATE %s
                        SET provider = ?, remote_url = ?, default_branch = ?, bundle_path = ?,
                            credential_object_path = ?, updated_at = ?
                        WHERE app_id = ?
                        """.formatted(table),
                provider,
                remoteUrl,
                defaultBranch,
                bundlePath,
                credentialObjectPath,
                Timestamp.from(now),
                appId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                    """
                            INSERT INTO %s (
                                app_id, provider, remote_url, default_branch, bundle_path,
                                credential_object_path, updated_at
                            ) VALUES (?, ?, ?, ?, ?, ?, ?)
                            """.formatted(table),
                    appId,
                    provider,
                    remoteUrl,
                    defaultBranch,
                    bundlePath,
                    credentialObjectPath,
                    Timestamp.from(now)
            );
        }
    }

    public void clear(String appId) {
        jdbcTemplate.update("DELETE FROM " + table + " WHERE app_id = ?", appId);
    }
}
