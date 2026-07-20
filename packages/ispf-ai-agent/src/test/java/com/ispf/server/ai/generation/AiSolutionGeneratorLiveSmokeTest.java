package com.ispf.server.ai.generation;

import com.ispf.server.automation.AutomationTreeService;
import com.ispf.server.object.ObjectManager;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * BL-180: multi-domain plant description → tree + dashboard + alert when {@code ISPF_LLM_SMOKE=true}.
 * <p>
 * Default matrix: HVAC building, MES/factory, SCADA/plant.
 * Pin one domain with {@code AGENT_LIVE_GENERATOR_DOMAIN=hvac|mes|scada} (field-soak oneshot).
 * Soft &lt;15 min is recorded to {@code build/agent-regression/live-generator-results.json}
 * (or {@code AGENT_LIVE_GENERATOR_RESULTS}), then assumed — not a hard GA fail.
 * Skipped cleanly when {@code ISPF_LLM_SMOKE} is unset (no AI secrets required for compile/CI unit path).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSolutionGeneratorLiveSmokeTest {

    private static final Logger log = LoggerFactory.getLogger(AiSolutionGeneratorLiveSmokeTest.class);

    /** Default multi-domain live apply matrix (BL-180). */
    static final List<String> DEFAULT_LIVE_DOMAINS = List.of("hvac", "mes", "scada");

    @Autowired
    private AiSolutionGeneratorService solutionGeneratorService;

    @Autowired
    private ObjectManager objectManager;

    @Autowired
    private MockMvc mockMvc;

    @DynamicPropertySource
    static void llmSmokeProperties(DynamicPropertyRegistry registry) {
        registry.add("ispf.ai.enabled", () -> "true");
        registry.add("ispf.ai.provider", () -> env("ISPF_AI_PROVIDER", "openai-compatible"));
        registry.add("ispf.ai.base-url", () -> env("ISPF_AI_BASE_URL", "http://127.0.0.1:8000/v1"));
        registry.add("ispf.ai.model", () -> env("ISPF_AI_MODEL", "gpt-4o-mini"));
        registry.add("ispf.ai.timeout-seconds", () -> env("ISPF_AI_TIMEOUT_SECONDS", "120"));
        String apiKey = firstNonBlank(System.getenv("ISPF_AI_API_KEY"), System.getenv("OPENAI_API_KEY"));
        if (apiKey != null && !apiKey.isBlank()) {
            registry.add("ispf.ai.api-key", () -> apiKey);
        }
    }

    static Stream<Arguments> liveDomainPrompts() {
        Stream<Arguments> all = Stream.of(
                Arguments.of(
                        "hvac",
                        """
                        Describe a building HVAC plant with one AHU and zone comfort monitoring. \
                        Need an overview dashboard and a high-status alert on the hub.
                        """
                ),
                Arguments.of(
                        "mes",
                        """
                        Describe a factory MES cell with one packaging line and OEE monitoring. \
                        Need an overview dashboard and a high-status alert on the hub.
                        """
                ),
                Arguments.of(
                        "scada",
                        """
                        Describe a SCADA plant with one pump station and tank level monitoring. \
                        Need an overview dashboard and a high-status alert on the hub.
                        """
                )
        );
        String pin = System.getenv("AGENT_LIVE_GENERATOR_DOMAIN");
        if (pin == null || pin.isBlank()) {
            return all;
        }
        String wanted = pin.trim().toLowerCase(Locale.ROOT);
        return all.filter(args -> wanted.equals(String.valueOf(args.get()[0]).toLowerCase(Locale.ROOT)));
    }

    /**
     * Always-on matrix check (no LLM secrets). Live apply cases below are gated by {@code ISPF_LLM_SMOKE}.
     */
    @Test
    void defaultLiveDomainMatrixConfigured() {
        assertEquals(3, DEFAULT_LIVE_DOMAINS.size());
        assertTrue(DEFAULT_LIVE_DOMAINS.contains("hvac"));
        assertTrue(DEFAULT_LIVE_DOMAINS.contains("mes"));
        assertTrue(DEFAULT_LIVE_DOMAINS.contains("scada"));
        assertEquals(3, liveDomainPrompts().count());
    }

    @ParameterizedTest(name = "domain={0}")
    @MethodSource("liveDomainPrompts")
    @EnabledIfEnvironmentVariable(named = "ISPF_LLM_SMOKE", matches = "true")
    @Timeout(value = 16, unit = TimeUnit.MINUTES)
    void plantDescriptionAppliesTreeDashboardAndAlert(String domain, String prompt) throws Exception {
        long startedMs = System.currentTimeMillis();
        boolean functionalOk = false;
        String appId = "";
        String hubPath = "";
        String alertPath = "";
        String dashboardPath = "";
        Duration elapsed = Duration.ZERO;
        try {
            Map<String, Object> result = solutionGeneratorService.generate(prompt, true, "admin");
            elapsed = Duration.ofMillis(System.currentTimeMillis() - startedMs);

            assertEquals("live", result.get("mode"), () -> domain + " unexpected result: " + result);
            assertEquals("primitives", result.get("composition"), () -> domain + " unexpected result: " + result);
            assertNotNull(result.get("appId"), () -> domain + " missing appId: " + result);
            assertNotNull(result.get("hubPath"), () -> domain + " missing hubPath: " + result);
            assertNotNull(result.get("alertPath"), () -> domain + " missing alertPath: " + result);
            assertNotNull(result.get("dashboardPath"), () -> domain + " missing dashboardPath: " + result);

            appId = String.valueOf(result.get("appId"));
            hubPath = String.valueOf(result.get("hubPath"));
            alertPath = String.valueOf(result.get("alertPath"));
            dashboardPath = String.valueOf(result.get("dashboardPath"));

            assertTrue(objectManager.tree().findByPath(hubPath).isPresent(), domain + " hub missing: " + hubPath);
            assertTrue(
                    objectManager.require(hubPath).getVariable("status").isPresent(),
                    domain + " status variable missing on hub"
            );
            assertTrue(objectManager.tree().findByPath(alertPath).isPresent(), domain + " alert missing: " + alertPath);
            assertTrue(
                    objectManager.tree().findByPath(dashboardPath).isPresent(),
                    domain + " dashboard missing: " + dashboardPath
            );

            var ui = mockMvc.perform(get("/api/v1/operator-apps/" + appId + "/ui")).andReturn();
            assertEquals(200, ui.getResponse().getStatus(), ui.getResponse().getContentAsString());
            assertTrue(alertPath.startsWith(AutomationTreeService.ALERT_RULES_ROOT));
            functionalOk = true;
        } finally {
            if (elapsed.isZero()) {
                elapsed = Duration.ofMillis(System.currentTimeMillis() - startedMs);
            }
            LiveGeneratorEvidence.record(
                    domain,
                    elapsed,
                    functionalOk,
                    appId,
                    hubPath,
                    dashboardPath,
                    alertPath
            );
        }

        final Duration recordedElapsed = elapsed;
        log.info(
                "BL-180 live apply domain={} elapsed={} softBudget={} evidence={}",
                domain,
                recordedElapsed,
                LiveGeneratorEvidence.SOFT_DURATION_BUDGET,
                LiveGeneratorEvidence.resultsPath()
        );
        Assumptions.assumeTrue(
                recordedElapsed.compareTo(LiveGeneratorEvidence.SOFT_DURATION_BUDGET) <= 0,
                () -> "soft duration budget exceeded for " + domain + ": " + recordedElapsed
                        + " (budget " + LiveGeneratorEvidence.SOFT_DURATION_BUDGET
                        + "); recorded in " + LiveGeneratorEvidence.resultsPath()
                        + " — soft signal, not GA fail"
        );
    }

    private static String env(String key, String defaultValue) {
        String value = System.getenv(key);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        if (b != null && !b.isBlank()) {
            return b;
        }
        return null;
    }
}
