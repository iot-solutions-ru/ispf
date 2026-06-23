package com.ispf.server.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformSqlApiTest {

    private static final String PACKAGE_ID = "sql-editor-test";
    private static final String DATA_SOURCE_PATH = "root.platform.data-sources.sql-editor-test";
    private static final String MIGRATION_PATH = "root.platform.migrations.editor_items";
    private static final String BINDING_PATH = "root.platform.bindings.editor-count";
    private static final String TARGET_OBJECT = "root.platform";
    private static final String TARGET_VARIABLE = "sqlEditorBindingValue";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void ensureSchema() throws Exception {
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", PACKAGE_ID)
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "SQL Editor Test",
                                  "schemaName": "app_sql_editor_test",
                                  "migrations": []
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void dataSourceCrud() throws Exception {
        mockMvc.perform(get("/api/v1/data-sources/by-path")
                        .header("X-ISPF-Role", "admin")
                        .param("path", DATA_SOURCE_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.schemaName").value("app_sql_editor_test"));

        mockMvc.perform(put("/api/v1/data-sources/by-path")
                        .header("X-ISPF-Role", "admin")
                        .param("path", DATA_SOURCE_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "SQL Editor Test DS",
                                  "schemaName": "app_sql_editor_test",
                                  "description": "Updated"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Updated"));
    }

    @Test
    void migrationCreateApplyAndBindingRefresh() throws Exception {
        mockMvc.perform(post("/api/v1/migrations")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "scriptId": "editor_items",
                                  "version": "1.0.0",
                                  "dataSourcePath": "%s",
                                  "sql": "CREATE TABLE IF NOT EXISTS editor_items (id UUID PRIMARY KEY, status VARCHAR(32) NOT NULL);"
                                }
                                """.formatted(DATA_SOURCE_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(MIGRATION_PATH))
                .andExpect(jsonPath("$.applied").value(false));

        mockMvc.perform(post("/api/v1/migrations/by-path/apply")
                        .header("X-ISPF-Role", "admin")
                        .param("path", MIGRATION_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.migration.applied").value(true));

        mockMvc.perform(post("/api/v1/sql-bindings")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "bindingId": "editor-count",
                                  "targetObjectPath": "%s",
                                  "variable": "%s",
                                  "dataSourcePath": "%s",
                                  "query": "SELECT 1 AS cnt",
                                  "valueField": "cnt",
                                  "refresh": "manual",
                                  "enabled": true
                                }
                                """.formatted(TARGET_OBJECT, TARGET_VARIABLE, DATA_SOURCE_PATH)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.path").value(BINDING_PATH));

        mockMvc.perform(post("/api/v1/sql-bindings/by-path/refresh")
                        .header("X-ISPF-Role", "admin")
                        .param("path", BINDING_PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.binding.lastRefreshedAt").isNotEmpty());
    }
}
