package com.ispf.server.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcUrlSafetyTest {

    @Test
    void acceptsSupportedSchemes() {
        assertThat(JdbcUrlSafety.requireSafeJdbcUrl("jdbc:postgresql://db/ispf"))
                .startsWith("jdbc:postgresql:");
        assertThat(JdbcUrlSafety.requireSafeJdbcUrl("jdbc:h2:mem:test"))
                .startsWith("jdbc:h2:");
        assertThat(JdbcUrlSafety.requireSafeJdbcUrl("jdbc:mysql://localhost/db"))
                .startsWith("jdbc:mysql:");
    }

    @Test
    void rejectsBlank() {
        assertThatThrownBy(() -> JdbcUrlSafety.requireSafeJdbcUrl("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("required");
    }

    @Test
    void rejectsExoticSchemes() {
        assertThatThrownBy(() -> JdbcUrlSafety.requireSafeJdbcUrl("jdbc:rmi://localhost/obj"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
        assertThatThrownBy(() -> JdbcUrlSafety.requireSafeJdbcUrl("ldap://evil"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not allowed");
    }
}
