package com.ispf.server.mimic;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class MimicWriteAclApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void developerCannotSaveDiagramWhenWriteRolesRestrictToAdmin() throws Exception {
        String admin = login("admin", "admin");
        String developer = login("developer", "developer");
        String name = "acl-diagram-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        String path = "root.platform.mimics." + name;

        mockMvc.perform(post("/api/v1/objects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.mimics",
                                  "name": "%s",
                                  "type": "MIMIC",
                                  "displayName": "ACL diagram test",
                                  "templateId": "mimic-v1"
                                }
                                """.formatted(name)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/mimics/by-path")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path))
                .andExpect(status().isOk());

        mockMvc.perform(patch("/api/v1/objects/by-path/variables")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path)
                        .param("name", "diagram")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "writeRoles": ["admin"] }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/mimics/by-path/diagram")
                        .header("Authorization", "Bearer " + developer)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagramJson":"{\\"elements\\":[],\\"connections\\":[]}"}
                                """))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/mimics/by-path/diagram")
                        .header("Authorization", "Bearer " + admin)
                        .param("path", path)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"diagramJson":"{\\"elements\\":[],\\"connections\\":[]}"}
                                """))
                .andExpect(status().isOk());
    }

    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "username": "%s", "password": "%s" }
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return body.replaceAll("(?s).*\"token\"\\s*:\\s*\"([^\"]+)\".*", "$1");
    }
}
