package com.ispf.server.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PlatformJobApiTest {

    private static final String PACKAGE_ID = "platform-job-test";
    private static final String DATA_SOURCE_PATH = "root.platform.data-sources.platform-job-test";
    private static final String REPORT_PATH = "root.platform.reports.platform-job-test-report";

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void ensureDataSourceAndSchema() throws Exception {
        mockMvc.perform(post("/api/v1/platform/packages/import")
                        .param("packageId", PACKAGE_ID)
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "version": "1.0.0",
                                  "displayName": "Platform Job Test",
                                  "schemaName": "app_platform_job_test",
                                  "migrations": [
                                    {
                                      "id": "platform_item",
                                      "sql": "CREATE TABLE IF NOT EXISTS platform_item (id UUID PRIMARY KEY, item_code VARCHAR(64) NOT NULL, status VARCHAR(32) NOT NULL);"
                                    }
                                  ]
                                }
                                """))
                .andExpect(status().isOk());
    }

    @Test
    void asyncReportRunCompletesViaJobQueue() throws Exception {
        mockMvc.perform(post("/api/v1/objects")
                        .header("X-ISPF-Role", "admin")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "parentPath": "root.platform.reports",
                                  "name": "platform-job-test-report",
                                  "type": "REPORT",
                                  "displayName": "Platform Job Test Report",
                                  "templateId": "report-v1"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/v1/reports/by-path/definition")
                        .header("X-ISPF-Role", "admin")
                        .param("path", REPORT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Platform Job Test Report",
                                  "dataSourcePath": "%s",
                                  "query": "SELECT item_code, status FROM platform_item",
                                  "parameters": [],
                                  "columns": [
                                    {"field": "item_code", "label": "Code"},
                                    {"field": "status", "label": "Status"}
                                  ],
                                  "maxRows": 100
                                }
                                """.formatted(DATA_SOURCE_PATH)))
                .andExpect(status().isOk());

        String body = mockMvc.perform(post("/api/v1/reports/by-path/run-async")
                        .header("X-ISPF-Role", "admin")
                        .param("path", REPORT_PATH)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status", is("QUEUED")))
                .andReturn()
                .getResponse()
                .getContentAsString();

        String jobId = body.replaceAll(".*\"jobId\"\\s*:\\s*\"([^\"]+)\".*", "$1");
        UUID.fromString(jobId);

        for (int attempt = 0; attempt < 30; attempt++) {
            var result = mockMvc.perform(get("/api/v1/platform/jobs/" + jobId)
                            .header("X-ISPF-Role", "admin"))
                    .andExpect(status().isOk());
            String status = result.andReturn().getResponse().getContentAsString();
            if (status.contains("\"COMPLETED\"")) {
                result.andExpect(jsonPath("$.result.reportId").exists());
                return;
            }
            if (status.contains("\"FAILED\"")) {
                throw new AssertionError("Job failed: " + status);
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
        throw new AssertionError("Job did not complete in time");
    }
}
