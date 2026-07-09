package com.ispf.server.alert;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Isolated
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AlarmShelfApiTest {

    private static final String DEMO_DEVICE = "root.platform.devices.demo-sensor-01";
    private static final String EVENT_NAME = "thresholdExceeded";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AlarmShelfService alarmShelfService;

    @AfterEach
    void cleanupShelves() {
        alarmShelfService.listActive().stream()
                .filter(shelf -> DEMO_DEVICE.equals(shelf.objectPath()) && EVENT_NAME.equals(shelf.eventName()))
                .forEach(shelf -> alarmShelfService.unshelve(shelf.id()));
    }

    @Test
    void shelveListAndUnshelve() throws Exception {
        String createBody = """
                {
                  "objectPath": "%s",
                  "eventName": "%s",
                  "durationMinutes": 30,
                  "comment": "maintenance window"
                }
                """.formatted(DEMO_DEVICE, EVENT_NAME);

        MvcResult created = mockMvc.perform(post("/api/v1/alarm-shelves")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.objectPath").value(DEMO_DEVICE))
                .andExpect(jsonPath("$.eventName").value(EVENT_NAME))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();

        String shelfId = JsonPath.read(created.getResponse().getContentAsString(), "$.id");

        mockMvc.perform(get("/api/v1/alarm-shelves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(greaterThanOrEqualTo(1)));

        mockMvc.perform(delete("/api/v1/alarm-shelves/" + shelfId))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/alarm-shelves"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + shelfId + "')]").isEmpty());
    }
}
