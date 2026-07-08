package com.ispf.server.security.mfa;

import com.ispf.server.application.data.PlatformSqlCatalog;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public class MfaEnrollmentStore {

    private final JdbcTemplate jdbcTemplate;
    private final String enrollmentsTable;

    public MfaEnrollmentStore(JdbcTemplate jdbcTemplate, PlatformSqlCatalog platformSqlCatalog) {
        this.jdbcTemplate = jdbcTemplate;
        this.enrollmentsTable = platformSqlCatalog.table("mfa_enrollments");
    }

    public void savePending(String username, String secret, Instant startedAt) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM %s WHERE username = ?".formatted(enrollmentsTable),
                Integer.class,
                username
        );
        if (count != null && count > 0) {
            jdbcTemplate.update("""
                    UPDATE %s
                    SET secret = ?, enrolled_at = NULL, created_at = ?
                    WHERE username = ?
                    """.formatted(enrollmentsTable),
                    secret,
                    Timestamp.from(startedAt),
                    username
            );
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO %s (username, secret, enrolled_at, created_at)
                VALUES (?, ?, NULL, ?)
                """.formatted(enrollmentsTable),
                username,
                secret,
                Timestamp.from(startedAt)
        );
    }

    public void confirmEnrollment(String username, Instant enrolledAt) {
        jdbcTemplate.update("""
                UPDATE %s
                SET enrolled_at = ?
                WHERE username = ? AND enrolled_at IS NULL
                """.formatted(enrollmentsTable),
                Timestamp.from(enrolledAt),
                username
        );
    }

    public void deletePending(String username) {
        jdbcTemplate.update("""
                DELETE FROM %s
                WHERE username = ? AND enrolled_at IS NULL
                """.formatted(enrollmentsTable),
                username
        );
    }

    public Optional<MfaEnrollment> findByUsername(String username) {
        List<MfaEnrollment> rows = jdbcTemplate.query("""
                SELECT username, secret, enrolled_at, created_at
                FROM %s
                WHERE username = ?
                """.formatted(enrollmentsTable),
                (rs, rowNum) -> new MfaEnrollment(
                        rs.getString("username"),
                        rs.getString("secret"),
                        rs.getTimestamp("enrolled_at") != null
                                ? rs.getTimestamp("enrolled_at").toInstant()
                                : null,
                        rs.getTimestamp("created_at").toInstant()
                ),
                username
        );
        return rows.isEmpty() ? Optional.empty() : Optional.of(rows.getFirst());
    }

    public record MfaEnrollment(
            String username,
            String secret,
            Instant enrolledAt,
            Instant createdAt
    ) {
        public boolean isEnrolled() {
            return enrolledAt != null;
        }

        public boolean isPending() {
            return enrolledAt == null;
        }
    }
}
