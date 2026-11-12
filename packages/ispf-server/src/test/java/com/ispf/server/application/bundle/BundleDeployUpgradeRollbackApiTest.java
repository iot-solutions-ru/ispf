package com.ispf.server.application.bundle;

import com.ispf.server.application.tree.ApplicationObjectTreeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BundleDeployUpgradeRollbackApiTest {

    private static final String APP_ID = "bnd-upgrb";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApplicationBundleSnapshotStore snapshotStore;

    @MockitoSpyBean
    private ApplicationObjectTreeService objectTreeService;

    @MockitoSpyBean
    private BundleVisualGroupService bundleVisualGroupService;

    private int appSyncCount;

    @BeforeEach
    void resetSyncBehavior() {
        appSyncCount = 0;
        Mockito.reset(objectTreeService, bundleVisualGroupService);
        Mockito.doAnswer(invocation -> {
            String appId = invocation.getArgument(0);
            if (APP_ID.equals(appId)) {
                appSyncCount++;
                if (appSyncCount == 4) {
                    throw new RuntimeException("injected sync failure");
                }
            }
            return invocation.callRealMethod();
        }).when(objectTreeService).syncApplication(anyString());
    }

    @Test
    void failedUpgradePreservesActiveSnapshotAndRestoresRuntime() throws Exception {
        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifest("1.0.0", "bnd-v1-probe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));

        assertEquals("1.0.0", snapshotStore.findActive(APP_ID).orElseThrow().bundleVersion());

        mockMvc.perform(post("/api/v1/applications/" + APP_ID + "/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(manifest("1.1.0", "bnd-v2-probe")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARTIAL"))
                .andExpect(jsonPath("$.compensated", hasItem(containsString("restore:activeSnapshot:1.0.0"))));

        assertEquals("1.0.0", snapshotStore.findActive(APP_ID).orElseThrow().bundleVersion());
        verify(objectTreeService, atLeast(2)).syncApplication(APP_ID);
        verify(bundleVisualGroupService, never()).removeAllBundleGroups(APP_ID);
    }

    private static String manifest(String version, String objectName) {
        return """
                {
                  "version": "%s",
                  "displayName": "%s",
                  "schemaName": "app_bnd_upgrb",
                  "objects": [
                    { "parentPath": "root.platform.devices", "name": "%s", "type": "DEVICE" }
                  ]
                }
                """.formatted(version, APP_ID, objectName);
    }
}
