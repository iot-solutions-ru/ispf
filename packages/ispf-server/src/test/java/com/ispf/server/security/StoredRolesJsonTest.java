package com.ispf.server.security;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StoredRolesJsonTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void blankJsonIsAnEmptyRoleList() {
        assertThat(StoredRolesJson.read(objectMapper, null)).isEmpty();
        assertThat(StoredRolesJson.read(objectMapper, "  ")).isEmpty();
        assertThat(StoredRolesJson.read(objectMapper, "[]")).isEmpty();
    }

    @Test
    void readableJsonKeepsTheRoles() {
        assertThat(StoredRolesJson.read(objectMapper, "[\"ADMIN\",\"OPERATOR\"]"))
                .containsExactly("ADMIN", "OPERATOR");
    }

    @Test
    void corruptJsonIsAnAccountError() {
        assertThatThrownBy(() -> StoredRolesJson.read(objectMapper, "{not-json"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Corrupt roles JSON");
    }
}
