package com.ispf.server.federation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class FederationSecretsKeyStore {

    private static final String SINGLETON_ID = "X";

    private final JdbcTemplate jdbcTemplate;

    public FederationSecretsKeyStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findKey() {
        List<String> rows = jdbcTemplate.query(
                "SELECT secrets_key FROM platform_federation_secrets WHERE id = ?",
                (rs, rowNum) -> rs.getString("secrets_key"),
                SINGLETON_ID
        );
        return rows.stream().findFirst().filter(value -> value != null && !value.isBlank());
    }

    public void save(String secretsKey) {
        Instant now = Instant.now();
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM platform_federation_secrets WHERE id = ?",
                Integer.class,
                SINGLETON_ID
        );
        if (count != null && count > 0) {
            jdbcTemplate.update(
                    "UPDATE platform_federation_secrets SET secrets_key = ?, updated_at = ? WHERE id = ?",
                    secretsKey,
                    Timestamp.from(now),
                    SINGLETON_ID
            );
            return;
        }
        jdbcTemplate.update(
                "INSERT INTO platform_federation_secrets (id, secrets_key, created_at, updated_at) VALUES (?, ?, ?, ?)",
                SINGLETON_ID,
                secretsKey,
                Timestamp.from(now),
                Timestamp.from(now)
        );
    }
}
