package com.ispf.server.ai.mcp;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"test", "mcp"})
class McpToolApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void initializeReturnsServerInfo() throws Exception {
        mockMvc.perform(post("/api/v1/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.serverInfo.name").value("ispf-platform"));
    }

    @Test
    void toolsListIncludesPlatformTools() throws Exception {
        mockMvc.perform(post("/api/v1/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":2,"method":"tools/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.tools[?(@.name == 'list_objects')]").exists())
                .andExpect(jsonPath("$.result.tools[?(@.name == 'search_context')]").exists());
    }

    @Test
    void toolsCallListObjects() throws Exception {
        mockMvc.perform(post("/api/v1/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 3,
                                  "method": "tools/call",
                                  "params": {
                                    "name": "list_objects",
                                    "arguments": {"parent": "root.platform.devices", "lite": true}
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.isError").value(false))
                .andExpect(jsonPath("$.result.content[0].type").value("text"));
    }

    @Test
    void resourcesListIncludesContextPackSlices() throws Exception {
        mockMvc.perform(post("/api/v1/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"jsonrpc":"2.0","id":4,"method":"resources/list","params":{}}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.resources[?(@.uri == 'contextpack://info')]").exists())
                .andExpect(jsonPath("$.result.resources[?(@.uri == 'contextpack://script-steps')]").exists());
    }

    @Test
    void resourcesReadInfoSlice() throws Exception {
        mockMvc.perform(post("/api/v1/ai/mcp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "jsonrpc": "2.0",
                                  "id": 5,
                                  "method": "resources/read",
                                  "params": {"uri": "contextpack://info"}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.contents[0].mimeType").value("application/json"))
                .andExpect(jsonPath("$.result.contents[0].text").isNotEmpty());
    }
}
