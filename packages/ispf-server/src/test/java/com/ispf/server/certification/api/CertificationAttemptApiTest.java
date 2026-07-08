package com.ispf.server.certification.api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BL-190: certification exam runner stub.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CertificationAttemptApiTest {

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    void assumeQuestionBankPresent() {
        Path repoRoot = Path.of(System.getProperty("user.dir"));
        for (int depth = 0; depth <= 4; depth++) {
            Path bank = repoRoot.resolve("examples/certification/solution-developer-l1.json");
            if (Files.isRegularFile(bank)) {
                return;
            }
            Path parent = repoRoot.getParent();
            if (parent == null) {
                break;
            }
            repoRoot = parent;
        }
        org.junit.jupiter.api.Assumptions.assumeTrue(
                false,
                "examples/certification not found"
        );
    }

    @Test
    void gradesPerfectAttemptAsPassed() throws Exception {
        mockMvc.perform(post("/api/v1/certification/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "track": "solution-developer",
                                  "level": 1,
                                  "answers": [
                                    {"questionId": "sd-l1-q01", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q02", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q03", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q04", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q05", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q06", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q07", "selectedIndex": 0},
                                    {"questionId": "sd-l1-q08", "selectedIndex": 0}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.track").value("solution-developer"))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.score").value(8))
                .andExpect(jsonPath("$.total").value(8))
                .andExpect(jsonPath("$.passed").value(true))
                .andExpect(jsonPath("$.attemptId").value(startsWith("cert-attempt-")));
    }

    @Test
    void gradesLowScoreAsFailed() throws Exception {
        mockMvc.perform(post("/api/v1/certification/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "track": "solution-developer",
                                  "level": 1,
                                  "answers": [
                                    {"questionId": "sd-l1-q01", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q02", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q03", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q04", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q05", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q06", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q07", "selectedIndex": 1},
                                    {"questionId": "sd-l1-q08", "selectedIndex": 1}
                                  ]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.score").value(0))
                .andExpect(jsonPath("$.passed").value(false));
    }

    @Test
    void rejectsUnknownTrack() throws Exception {
        mockMvc.perform(post("/api/v1/certification/attempt")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "track": "unknown",
                                  "level": 1,
                                  "answers": [{"questionId": "x", "selectedIndex": 0}]
                                }
                                """))
                .andExpect(status().isBadRequest());
    }
}
