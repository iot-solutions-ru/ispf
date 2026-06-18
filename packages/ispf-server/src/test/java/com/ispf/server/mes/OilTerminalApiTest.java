package com.ispf.server.mes;

import com.ispf.plugin.oilterminal.OilTerminalConstants;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OilTerminalApiTest {

    private static final String ORDER = OilTerminalConstants.orderPath(OilTerminalConstants.DEMO_ORDER);
    private static final String RACK = OilTerminalConstants.rackPath(OilTerminalConstants.DEMO_RACK);

    @Autowired
    private MockMvc mockMvc;

    @Test
    void seedsOilTerminalTree() throws Exception {
        mockMvc.perform(get("/api/v1/objects/by-path").param("path", OilTerminalConstants.ROOT))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Oil Terminal"));

        mockMvc.perform(get("/api/v1/objects/by-path").param("path", ORDER))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value(OilTerminalConstants.DEMO_ORDER));
    }

    @Test
    void runsDispatchLifecycle() throws Exception {
        mockMvc.perform(post("/api/v1/oil/dispatch/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "4522",
                                  "productCode": "DT",
                                  "plannedLiters": 20000,
                                  "vehiclePlate": "A123BC77",
                                  "orderName": "dispatch4522"
                                }
                                """))
                .andExpect(status().isOk());

        String order = OilTerminalConstants.orderPath("dispatch4522");

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", order)
                        .param("name", "assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "schema": {
                                    "name": "assignInput",
                                    "fields": [
                                      {"name": "tankName", "type": "STRING"},
                                      {"name": "rackName", "type": "STRING"}
                                    ]
                                  },
                                  "rows": [{"tankName": "rvs3", "rackName": "rack2"}]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", order)
                        .param("name", "status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value("ready"));

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", order)
                        .param("name", "start"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", RACK)
                        .param("name", "busy"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(true));

        Thread.sleep(1500);

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", order)
                        .param("name", "actualLiters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value(greaterThan(0.0)));

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", order)
                        .param("name", "complete"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", order)
                        .param("name", "status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value("completed"));

        mockMvc.perform(post("/api/v1/objects/by-path/functions/invoke")
                        .param("path", order)
                        .param("name", "close"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].success").value(true));

        mockMvc.perform(get("/api/v1/objects/by-path/variables/detail")
                        .param("path", order)
                        .param("name", "status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value.rows[0].value").value("closed"));
    }

    @Test
    void importsDispatchOrderFromErpStub() throws Exception {
        mockMvc.perform(post("/api/v1/oil/dispatch/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orderNo": "7777",
                                  "productCode": "DT",
                                  "plannedLiters": 10000,
                                  "vehiclePlate": "B777BB77",
                                  "orderName": "dispatch7777"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderNo").value("7777"))
                .andExpect(jsonPath("$.status").value("planned"));
    }
}
