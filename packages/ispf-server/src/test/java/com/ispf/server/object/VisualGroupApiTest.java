package com.ispf.server.object;

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
class VisualGroupApiTest {

    private static final String DEVICE = "root.platform.devices.demo-sensor-01";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Test
    void listGroupMembersAsTreeChildren() throws Exception {
        String groupPath = "root.platform.devices.api-visual-group";
        if (objectManager.tree().findByPath(groupPath).isEmpty()) {
            objectManager.create(
                    "root.platform.devices",
                    "api-visual-group",
                    com.ispf.core.object.ObjectType.VISUAL_GROUP,
                    "API Visual Group",
                    "",
                    null
            );
        }

        mockMvc.perform(put("/api/v1/objects/by-path/group-members")
                        .param("path", groupPath)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "action": "set", "members": [
                                  { "path": "%s", "sortOrder": 0 }
                                ]}
                                """.formatted(DEVICE)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/objects").param("parent", groupPath).param("lite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].path").value(DEVICE))
                .andExpect(jsonPath("$[0].groupRef").value(true))
                .andExpect(jsonPath("$[0].groupContextPath").value(groupPath));

        objectManager.delete(groupPath);
    }

    @Test
    void bulkDeleteRejectsProtectedPaths() throws Exception {
        mockMvc.perform(post("/api/v1/objects/bulk-delete")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "paths": ["root", "root.platform"] }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(0))
                .andExpect(jsonPath("$.results[0].success").value(false))
                .andExpect(jsonPath("$.results[1].success").value(false));
    }
}
