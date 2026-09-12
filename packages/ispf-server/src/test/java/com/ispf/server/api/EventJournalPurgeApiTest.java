package com.ispf.server.api;

import com.ispf.server.event.EventJournalRecord;
import com.ispf.server.event.EventJournalStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true",
        "ispf.event-journal.enabled=true",
        "ispf.event-journal.async-enabled=false"
})
class EventJournalPurgeApiTest {

    private static final String TARGET = "root.platform.devices.journal-purge-target";
    private static final String OTHER = "root.platform.devices.journal-purge-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EventJournalStore eventJournalStore;

    @Test
    void adminPurgesManyRowsInOneCallAndDeveloperIsForbidden() throws Exception {
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Instant recent = Instant.parse("2026-09-01T00:00:00Z");
        Instant cutoff = Instant.parse("2024-01-01T00:00:00Z");
        long before = eventJournalStore.countTotal();

        String run = java.util.UUID.randomUUID().toString().substring(0, 8);
        List<EventJournalRecord> rows = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            rows.add(record("old-" + run + "-" + i, TARGET, old));
        }
        rows.add(record("keep-target-" + run, TARGET, recent));
        rows.add(record("keep-other-" + run, OTHER, old));
        eventJournalStore.appendBatch(rows);

        String developer = login("developer", "developer");
        mockMvc.perform(post("/api/v1/events/journal/purge")
                        .header("Authorization", "Bearer " + developer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"olderThan":"%s","objectPath":"%s"}
                                """.formatted(cutoff, TARGET)))
                .andExpect(status().isForbidden());

        String admin = login("admin", "admin");
        mockMvc.perform(post("/api/v1/events/journal/purge")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"olderThan":"%s","objectPath":"%s"}
                                """.formatted(cutoff, TARGET)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(25));

        assertEquals(before + 2, eventJournalStore.countTotal());
    }

    private static EventJournalRecord record(String id, String path, Instant occurredAt) {
        return new EventJournalRecord(id, path, "thresholdExceeded", "WARNING", "{}", occurredAt);
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
