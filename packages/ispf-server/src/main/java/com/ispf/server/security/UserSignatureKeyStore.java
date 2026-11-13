package com.ispf.server.security;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class UserSignatureKeyStore implements SignatureKeyDirectory {

    private final JdbcTemplate jdbcTemplate;
    private final String table;

    public UserSignatureKeyStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.table = platformSqlCatalog.table("user_signature_keys");
    }

    @Override
    public void upsert(String username, String publicKey) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE username = ?".formatted(table),
                Integer.class,
                username
        );
        Timestamp now = Timestamp.from(Instant.now());
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE %s SET public_key = ?, updated_at = ? WHERE username = ?".formatted(table),
                    publicKey,
                    now,
                    username
            );
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO %s (username, public_key, updated_at) VALUES (?, ?, ?)".formatted(table),
                username,
                publicKey,
                now
        );
    }

    @Override
    public Optional<String> find(String username) {
        List<String> keys = jdbcTemplate.query(
                "SELECT public_key FROM %s WHERE username = ?".formatted(table),
                (rs, rowNum) -> rs.getString(1),
                username
        );
        return keys.stream().findFirst();
    }

    @Override
    public void delete(String username) {
        jdbcTemplate.update(
                "DELETE FROM %s WHERE username = ?".formatted(table),
                username
        );
    }
}
