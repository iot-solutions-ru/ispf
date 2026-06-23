package com.ispf.server.api;

import com.ispf.core.object.ObjectType;
import com.ispf.core.object.PlatformObject;
import com.ispf.server.bootstrap.LabModelBootstrap;
import com.ispf.server.object.ObjectManager;
import com.ispf.server.object.ObjectTemplateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
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
class ObjectCreateTemplateApiTest {

    private static final String PARENT = "root.platform.devices";
    private static final String NAME = "template-create-test-01";
    private static final String PATH = PARENT + "." + NAME;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private ObjectTemplateService objectTemplateService;

    @BeforeEach
    void removeFixtureIfPresent() {
        objectManager.require(PARENT);
        objectManager.tree().findByPath(PATH).ifPresent(node -> objectManager.delete(PATH));
    }

    @Test
    void createAppliesTemplateVariablesAndFunctions() throws Exception {
        PlatformObject node = objectManager.create(
                PARENT,
                NAME,
                ObjectType.DEVICE,
                "Template create test",
                null,
                LabModelBootstrap.VIRTUAL_LAB_MODEL
        );
        objectTemplateService.applyTemplate(node.path(), LabModelBootstrap.VIRTUAL_LAB_MODEL);
        objectManager.persistNodeTree(node.path());

        mockMvc.perform(get("/api/v1/objects/by-path")
                        .param("path", PATH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.templateId").value(LabModelBootstrap.VIRTUAL_LAB_MODEL));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", PATH)
                        .param("name", "sineWave"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("sineWave"));

        mockMvc.perform(put("/api/v1/objects/by-path/variables")
                        .param("path", PATH)
                        .param("name", "intValue")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "integerValue",
                                    "fields": [{"name": "value", "type": "INTEGER"}]
                                  },
                                  "rows": [{"value": 42}]
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", PATH)
                        .param("name", "calculate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"rows": [{"inputA": 2.0, "inputB": 3.0}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].result").value(5.0));
    }
}
