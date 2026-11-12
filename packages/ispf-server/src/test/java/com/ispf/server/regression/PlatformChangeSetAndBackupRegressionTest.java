package com.ispf.server.regression;

import com.ispf.core.model.DataRecord;
import com.ispf.core.model.DataSchema;
import com.ispf.core.model.FieldType;
import com.ispf.core.object.ObjectType;
import com.ispf.server.object.ObjectEditLeaseService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Platform governance, promotion (P7 PROMOTE), collaboration locking/audit, and subtree
 * backup/restore round-trip regression tests:
 *
 * <ul>
 *   <li>{@code /api/v1/platform/change-sets}: create, list/filter, preview (UPDATE vs CREATE vs
 *       revision conflict), 409 rejection without {@code force}, transactional {@code force=true}
 *       apply, and 400 rollback on unsupported operations</li>
 *   <li>Collaboration: optimistic concurrency ({@code If-Match} -&gt; 409, {@code X-ISPF-Force}),
 *       config audit trail ({@code /api/v1/objects/by-path/audit}), and subtree edit leases
 *       ({@code /api/v1/objects/leases} -&gt; 423 Locked for competing writers)</li>
 *   <li>{@code /api/v1/platform/backup}: full HTTP subtree export, {@code dryRun=true} preview
 *       without mutation, destructive delete, and {@code dryRun=false} full subtree + variable
 *       restoration</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformChangeSetAndBackupRegressionTest {

    private static final String CS_TARGET_PATH = "root.platform.devices.cs-reg-hub";
    private static final String CS_FUTURE_PATH = "root.platform.devices.cs-reg-nonexistent";
    private static final String LEASE_TARGET_PATH = "root.platform.devices.lease-reg-node";
    private static final String BACKUP_ROOT = "root.platform.devices.backup-reg-cell";
    private static final String BACKUP_HUB = BACKUP_ROOT + ".cell-hub";
    private static final String BACKUP_SENSOR = BACKUP_ROOT + ".sensor-a";

    private static final Authentication ADMIN_ALICE = UsernamePasswordAuthenticationToken.authenticated(
            "alice-admin",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ADMIN"), new SimpleGrantedAuthority("admin"))
    );
    private static final Authentication ENGINEER_BOB = UsernamePasswordAuthenticationToken.authenticated(
            "bob-engineer",
            "n/a",
            List.of(new SimpleGrantedAuthority("ROLE_ENGINEER"), new SimpleGrantedAuthority("engineer"))
    );

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectEditLeaseService editLeaseService;

    @AfterEach
    void cleanup() {
        editLeaseService.release(LEASE_TARGET_PATH, "alice-admin");
        deleteIfPresent(CS_TARGET_PATH);
        deleteIfPresent(CS_FUTURE_PATH);
        deleteIfPresent(LEASE_TARGET_PATH);
        deleteIfPresent(BACKUP_SENSOR);
        deleteIfPresent(BACKUP_HUB);
        deleteIfPresent(BACKUP_ROOT);
    }

    @Test
    void changeSetCreatePreviewConflictAndForceApplyLifecycle() throws Exception {
        objectManager.create(
                "root.platform.devices",
                "cs-reg-hub",
                ObjectType.CUSTOM,
                "Initial Hub Title",
                "v1 description",
                null
        );
        objectManager.persistNodeTree(CS_TARGET_PATH);
        long initialRevision = objectManager.require(CS_TARGET_PATH).revision();

        // 1. Create DRAFT change-set containing an UPDATE_INFO op (pinned to initialRevision)
        //    and an op targeting a not-yet-created path (classified as CREATE in preview).
        MvcResult createRes = mockMvc.perform(post("/api/v1/platform/change-sets")
                        .with(authentication(ADMIN_ALICE))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Promote cell metadata v2",
                                  "ops": [
                                    {
                                      "op": "UPDATE_INFO",
                                      "path": "%s",
                                      "expectedRevision": %d,
                                      "payload": {
                                        "displayName": "Promoted Hub Title v2",
                                        "description": "promoted via change-set"
                                      }
                                    },
                                    {
                                      "op": "SET_VARIABLE",
                                      "path": "%s",
                                      "payload": { "name": "setpoint" }
                                    }
                                  ]
                                }
                                """.formatted(CS_TARGET_PATH, initialRevision, CS_FUTURE_PATH)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.author").value("alice-admin"))
                .andExpect(jsonPath("$.ops", hasSize(2)))
                .andReturn();

        String changeSetId = objectMapper.readTree(createRes.getResponse().getContentAsString())
                .path("id")
                .asString();
        assertThat(changeSetId).isNotBlank();

        // 2. List & Get contracts (including 404 for unknown id)
        mockMvc.perform(get("/api/v1/platform/change-sets").param("status", "draft"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(changeSetId)));

        mockMvc.perform(get("/api/v1/platform/change-sets/{id}", changeSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Promote cell metadata v2"));

        mockMvc.perform(get("/api/v1/platform/change-sets/non-existent-cs-id"))
                .andExpect(status().isNotFound());

        // 3. Clean preview before concurrent edit -> 0 conflicts, 1 UPDATE + 1 CREATE
        mockMvc.perform(post("/api/v1/platform/change-sets/{id}/preview", changeSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount").value(0))
                .andExpect(jsonPath("$.applicable", hasSize(2)))
                .andExpect(jsonPath("$.applicable[*].status", hasItem("UPDATE")))
                .andExpect(jsonPath("$.applicable[*].status", hasItem("CREATE")));

        // 4. Simulate concurrent edit advancing revision -> preview detects conflict
        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .with(authentication(ENGINEER_BOB))
                        .param("path", CS_TARGET_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Concurrent Edit By Bob" }
                                """))
                .andExpect(status().isOk());

        long bumpedRevision = objectManager.require(CS_TARGET_PATH).revision();
        assertThat(bumpedRevision).isGreaterThan(initialRevision);

        mockMvc.perform(post("/api/v1/platform/change-sets/{id}/preview", changeSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.conflictCount").value(1))
                .andExpect(jsonPath("$.conflicts[0].path").value(CS_TARGET_PATH))
                .andExpect(jsonPath("$.conflicts[0].expectedRevision").value(initialRevision))
                .andExpect(jsonPath("$.conflicts[0].currentRevision").value(bumpedRevision));

        // 5. Apply with force=false refuses with 409 Conflict and preserves DRAFT state
        mockMvc.perform(post("/api/v1/platform/change-sets/{id}/apply", changeSetId)
                        .param("force", "false"))
                .andExpect(status().isConflict());

        mockMvc.perform(get("/api/v1/platform/change-sets/{id}", changeSetId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
        assertThat(objectManager.require(CS_TARGET_PATH).displayName()).isEqualTo("Concurrent Edit By Bob");

        // 6. Apply with force=true overwrites cleanly and marks change-set APPLIED
        mockMvc.perform(post("/api/v1/platform/change-sets/{id}/apply", changeSetId)
                        .param("force", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPLIED"))
                .andExpect(jsonPath("$.count").value(2));

        assertThat(objectManager.require(CS_TARGET_PATH).displayName()).isEqualTo("Promoted Hub Title v2");
        assertThat(objectManager.require(CS_TARGET_PATH).description()).isEqualTo("promoted via change-set");

        mockMvc.perform(get("/api/v1/platform/change-sets").param("status", "APPLIED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", hasItem(changeSetId)));
    }

    @Test
    void unsupportedChangeSetOperationFailsWith400AndLeavesDraftUnapplied() throws Exception {
        objectManager.create(
                "root.platform.devices",
                "cs-reg-hub",
                ObjectType.CUSTOM,
                "Stable Title",
                "stable",
                null
        );
        objectManager.persistNodeTree(CS_TARGET_PATH);

        MvcResult createRes = mockMvc.perform(post("/api/v1/platform/change-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Invalid op change-set",
                                  "ops": [
                                    {
                                      "op": "UPDATE_INFO",
                                      "path": "%s",
                                      "payload": { "displayName": "Should Rollback" }
                                    },
                                    {
                                      "op": "DROP_ENTIRE_UNIVERSE",
                                      "path": "%s",
                                      "payload": {}
                                    }
                                  ]
                                }
                                """.formatted(CS_TARGET_PATH, CS_TARGET_PATH)))
                .andExpect(status().isCreated())
                .andReturn();

        String id = objectMapper.readTree(createRes.getResponse().getContentAsString()).path("id").asString();

        mockMvc.perform(post("/api/v1/platform/change-sets/{id}/apply", id).param("force", "true"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/platform/change-sets/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void collaborationLeaseBlocksCompetingWriterWith423AndRecordsConfigAudit() throws Exception {
        objectManager.create(
                "root.platform.devices",
                "lease-reg-node",
                ObjectType.CUSTOM,
                "Lease Node v1",
                "initial",
                null
        );
        objectManager.persistNodeTree(LEASE_TARGET_PATH);

        // Acquire subtree lease as alice-admin
        editLeaseService.acquire(LEASE_TARGET_PATH, "alice-admin", Duration.ofMinutes(30));

        // Competing writer bob-engineer is blocked with 423 Locked
        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .with(authentication(ENGINEER_BOB))
                        .param("path", LEASE_TARGET_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Blocked Write By Bob" }
                                """))
                .andExpect(status().isLocked());

        // Lease holder alice-admin writes successfully; stale If-Match overridden via X-ISPF-Force
        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .with(authentication(ADMIN_ALICE))
                        .header("If-Match", "0")
                        .header("X-ISPF-Force", "true")
                        .param("path", LEASE_TARGET_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Authorized Force Update By Alice" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Authorized Force Update By Alice"));

        // Config audit trail records the metadata update
        mockMvc.perform(get("/api/v1/objects/by-path/audit")
                        .param("path", LEASE_TARGET_PATH)
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));

        // Release lease -> bob-engineer can now update
        editLeaseService.release(LEASE_TARGET_PATH, "alice-admin");
        mockMvc.perform(patch("/api/v1/objects/by-path")
                        .with(authentication(ENGINEER_BOB))
                        .param("path", LEASE_TARGET_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "displayName": "Unlocked Write By Bob" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Unlocked Write By Bob"));
    }

    @Test
    void backupExportDryRunAndFullSubtreeRestoreRoundTripOverHttp() throws Exception {
        // Build a 3-node subtree: folder + CUSTOM logic hub + DEVICE sensor with variables
        objectManager.create("root.platform.devices", "backup-reg-cell", ObjectType.CUSTOM, "Backup Cell", "folder", null);
        objectManager.create(BACKUP_ROOT, "cell-hub", ObjectType.CUSTOM, "Cell Hub", "orchestrator", null);
        objectManager.create(BACKUP_ROOT, "sensor-a", ObjectType.DEVICE, "Sensor A", "telemetry", null);

        DataSchema kpiSchema = DataSchema.builder("targetOee").field("value", FieldType.DOUBLE).build();
        objectManager.createVariable(
                BACKUP_HUB,
                "targetOee",
                kpiSchema,
                true,
                true,
                DataRecord.single(kpiSchema, Map.of("value", 92.5)),
                false,
                null
        );
        DataSchema tempSchema = DataSchema.builder("temperature").field("value", FieldType.DOUBLE).build();
        objectManager.createVariable(
                BACKUP_SENSOR,
                "temperature",
                tempSchema,
                true,
                true,
                DataRecord.single(tempSchema, Map.of("value", 36.6)),
                false,
                null
        );
        objectManager.persistNodeTree(BACKUP_ROOT);

        // 1. Export subtree over HTTP
        MvcResult exportRes = mockMvc.perform(get("/api/v1/platform/backup/export")
                        .param("rootPath", BACKUP_ROOT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootPath").value(BACKUP_ROOT))
                .andExpect(jsonPath("$.nodeCount").value(3))
                .andReturn();
        String exportedJson = exportRes.getResponse().getContentAsString();

        // 2. Delete entire subtree and verify nodes are gone
        mockMvc.perform(delete("/api/v1/objects/by-path").param("path", BACKUP_ROOT))
                .andExpect(status().isOk());
        assertThat(objectManager.tree().findByPath(BACKUP_ROOT)).isEmpty();
        assertThat(objectManager.tree().findByPath(BACKUP_HUB)).isEmpty();
        assertThat(objectManager.tree().findByPath(BACKUP_SENSOR)).isEmpty();

        // 3. Import with dryRun=true previews creation of 3 nodes without mutating tree
        mockMvc.perform(post("/api/v1/platform/backup/import")
                        .param("dryRun", "true")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exportedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(true))
                .andExpect(jsonPath("$.preview.createCount").value(3))
                .andExpect(jsonPath("$.created").value(0));
        assertThat(objectManager.tree().findByPath(BACKUP_ROOT)).isEmpty();

        // 4. Import with dryRun=false restores all nodes, types, metadata, and variable values
        mockMvc.perform(post("/api/v1/platform/backup/import")
                        .param("dryRun", "false")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exportedJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dryRun").value(false))
                .andExpect(jsonPath("$.created").value(3));

        assertThat(objectManager.require(BACKUP_HUB).type()).isEqualTo(ObjectType.CUSTOM);
        assertThat(objectManager.require(BACKUP_SENSOR).type()).isEqualTo(ObjectType.DEVICE);
        assertThat(objectManager.require(BACKUP_HUB)
                .getVariable("targetOee")
                .flatMap(v -> v.value())
                .map(r -> r.firstRow().get("value"))
                .orElse(null)).isEqualTo(92.5);
        assertThat(objectManager.require(BACKUP_SENSOR)
                .getVariable("temperature")
                .flatMap(v -> v.value())
                .map(r -> r.firstRow().get("value"))
                .orElse(null)).isEqualTo(36.6);
    }

    private void deleteIfPresent(String path) {
        if (objectManager.tree().findByPath(path).isPresent()) {
            objectManager.delete(path);
        }
    }
}
