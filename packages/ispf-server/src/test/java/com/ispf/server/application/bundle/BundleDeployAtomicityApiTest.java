package com.ispf.server.application.bundle;

import com.ispf.server.application.data.ApplicationDataStore;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleDeployAtomicityApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ApplicationDataStore applicationDataStore;

    @Test
    void successfulSqlThenWritesTheObject() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bnd2sqlok/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "bnd2sqlok",
                                "app_bnd2sqlok",
                                "bnd2-ok-probe",
                                "root.platform.devices",
                                "CREATE TABLE bnd2_ok_item (id INT PRIMARY KEY)"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.treeMutated").value(true))
                .andExpect(jsonPath("$.applied", hasItem("migration:init")));

        assertTrue(objectManager.tree().findByPath("root.platform.devices.bnd2-ok-probe").isPresent());
        assertEquals(1, tableCount("app_bnd2sqlok", "bnd2_ok_item"));
        assertTrue(applicationDataStore.isMigrationApplied("bnd2sqlok", "1.0.0", "init"));
    }

    @Test
    void failedSqlRollsBackAndDoesNotWriteTheObject() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bnd2sqlok/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "bnd2sqlok",
                                "app_bnd2sqlok",
                                "bnd2-ok-probe",
                                "root.platform.devices",
                                "CREATE TABLE bnd2_ok_item (id INT PRIMARY KEY)"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treeMutated").value(true));

        mockMvc.perform(post("/api/v1/applications/bnd2sqlbad/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "bnd2sqlbad",
                                "app_bnd2sqlbad",
                                "bnd2-ok-probe",
                                "root.platform.devices",
                                "CREATE TABLE bnd2_bad_item (id INT PRIMARY KEY); INSERT INTO bnd2_missing (id) VALUES (1)"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.treeMutated").value(false))
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.errors", hasItem(containsString("migrations:"))));

        assertTrue(objectManager.tree().findByPath("root.platform.devices.bnd2-ok-probe").isPresent());
        assertTrue(objectManager.tree().findByPath("root.platform.migrations.init").isPresent());
        assertEquals(1, tableCount("app_bnd2sqlok", "bnd2_ok_item"));
        assertEquals(0, tableCount("app_bnd2sqlbad", "bnd2_bad_item"));
        assertFalse(applicationDataStore.isMigrationApplied("bnd2sqlbad", "1.0.0", "init"));
    }

    @Test
    void dryRunFailureSkipsRegistrationSchemaSqlAndTheObjectTree() throws Exception {
        mockMvc.perform(post("/api/v1/applications/bnd2treebad/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(
                                "bnd2treebad",
                                "app_bnd2treebad",
                                "bnd2-orphan",
                                "root.missing.bnd2",
                                "CREATE TABLE bnd2_orphan_item (id INT PRIMARY KEY)"
                        )))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.treeMutated").value(false))
                .andExpect(jsonPath("$.errors", hasItem(containsString("parent does not exist"))));

        assertTrue(applicationDataStore.findApp("bnd2treebad").isEmpty());
        assertEquals(0, schemaCount("app_bnd2treebad"));
        assertFalse(objectManager.tree().findByPath("root.platform.applications.bnd2treebad").isPresent());
        assertFalse(objectManager.tree().findByPath("root.platform.data-sources.bnd2treebad").isPresent());
        assertFalse(objectManager.tree().findByPath("root.missing.bnd2.bnd2-orphan").isPresent());
        assertEquals(0, tableCount("app_bnd2treebad", "bnd2_orphan_item"));
        assertFalse(applicationDataStore.isMigrationApplied("bnd2treebad", "1.0.0", "init"));
    }

    @Test
    void dryRunRejectsDashboardWorkflowAndSingletonWithMissingParentsBeforeRegistration() throws Exception {
        String invalidParentsManifest = """
                {
                  "version": "1.0.0",
                  "displayName": "bnd2leafbad",
                  "schemaName": "app_bnd2leafbad",
                  "blueprints": [{
                    "name": "bad-hub",
                    "type": "SINGLETON",
                    "variables": [],
                    "events": [],
                    "functions": [],
                    "bindings": [],
                    "parameters": { "singletonInstancePath": "root.missing_hub_parent.bad-hub" }
                  }],
                  "dashboards": [{
                    "path": "root.missing_dash_parent.board",
                    "title": "Board",
                    "layoutJson": "{\\"widgets\\":[]}"
                  }],
                  "workflows": [{
                    "path": "root.missing_wf_parent.flow",
                    "title": "Flow",
                    "status": "DRAFT"
                  }]
                }
                """;

        mockMvc.perform(post("/api/v1/applications/bnd2leafbad/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidParentsManifest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.treeMutated").value(false))
                .andExpect(jsonPath("$.errors", hasItem(containsString("singletonInstancePath parent does not exist"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("dryRun:dashboard:"))))
                .andExpect(jsonPath("$.errors", hasItem(containsString("dryRun:workflow:"))));

        assertTrue(applicationDataStore.findApp("bnd2leafbad").isEmpty());
        assertEquals(0, schemaCount("app_bnd2leafbad"));
        assertFalse(objectManager.tree().findByPath("root.platform.applications.bnd2leafbad").isPresent());
        assertFalse(objectManager.tree().findByPath("root.platform.data-sources.bnd2leafbad").isPresent());
    }

    private int schemaCount(String schema) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.schemata
                WHERE LOWER(schema_name) = ?
                """, Integer.class, schema);
        return count != null ? count : 0;
    }

    private int tableCount(String schema, String table) {
        Integer count = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM information_schema.tables
                WHERE LOWER(table_schema) = ? AND LOWER(table_name) = ?
                """, Integer.class, schema, table);
        return count != null ? count : 0;
    }

    private static String body(String appId, String schema, String name, String parent, String sql) {
        return """
                {
                  "version": "1.0.0",
                  "displayName": "%s",
                  "schemaName": "%s",
                  "objects": [
                    { "parentPath": "%s", "name": "%s", "type": "DEVICE" }
                  ],
                  "migrations": [
                    { "id": "init", "sql": "%s" }
                  ]
                }
                """.formatted(appId, schema, parent, name, sql);
    }
}
