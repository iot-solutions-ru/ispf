package com.ispf.server.ai.generation;



import org.junit.jupiter.api.Test;



import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertThrows;



class AiSolutionGeneratorServiceTest {



    private final AiSolutionGeneratorService service = new AiSolutionGeneratorService();



    private static final Set<String> EXPECTED_DOMAINS = Set.of(

            "mes", "oil-gas", "water", "energy", "hvac", "building", "warehouse", "lab", "pipeline", "scada"

    );



    @Test

    void detectsAllTenDomains() {

        assertEquals("mes", service.generate("Deploy MES OEE dashboard").get("domain"));

        assertEquals("oil-gas", service.generate("Oil and gas pump station tank farm").get("domain"));

        assertEquals("water", service.generate("Wastewater treatment plant intake").get("domain"));

        assertEquals("energy", service.generate("Thermal power plant turbine kWh reporting").get("domain"));

        assertEquals("hvac", service.generate("HVAC zone comfort setpoints").get("domain"));

        assertEquals("building", service.generate("Smart building BMS lighting access control").get("domain"));

        assertEquals("warehouse", service.generate("Warehouse logistics cold chain inventory").get("domain"));

        assertEquals("lab", service.generate("Lab training virtual cluster simulator profile").get("domain"));

        assertEquals("pipeline", service.generate("Midstream pipeline valve station linepack").get("domain"));

        assertEquals("scada", service.generate("SCADA pump station mimic overview").get("domain"));

    }



    @Test

    void templateForCoversEveryDomain() {

        for (String domain : EXPECTED_DOMAINS) {

            String prompt = switch (domain) {

                case "mes" -> "MES manufacturing dispatch";

                case "oil-gas" -> "oil gas refinery";

                case "water" -> "water treatment";

                case "energy" -> "thermal power energy";

                case "hvac" -> "hvac comfort";

                case "building" -> "smart building bms";

                case "warehouse" -> "warehouse logistics";

                case "lab" -> "lab training";

                case "pipeline" -> "pipeline midstream";

                default -> "scada historian";

            };

            Map<String, Object> result = service.generate(prompt);

            assertEquals(domain, result.get("domain"), "domain mismatch for prompt: " + prompt);

            @SuppressWarnings("unchecked")

            Map<String, Object> draft = (Map<String, Object>) result.get("blueprintDraft");

            assertEquals(domain, draft.get("domain"));

            @SuppressWarnings("unchecked")

            Map<String, Object> bundle = (Map<String, Object>) draft.get("referenceBundle");

            assertEquals("stub", result.get("mode"));

            assertEquals(true, bundle.containsKey("appId"));

            assertEquals(true, bundle.containsKey("manifestPath"));

        }

    }



    @Test

    void hvacWinsOverBuildingWhenBothMentioned() {

        assertEquals("hvac", service.generate("HVAC building comfort zones with AHU setpoints").get("domain"));

    }



    @Test

    void blueprintDraftIncludesSpecBriefAndArtifacts() {

        Map<String, Object> result = service.generate("SCADA facility with historian trends");

        @SuppressWarnings("unchecked")

        Map<String, Object> draft = (Map<String, Object>) result.get("blueprintDraft");

        assertEquals("scada", draft.get("domain"));

        assertEquals("stub", result.get("mode"));

    }



    @Test

    void rejectsBlankPrompt() {

        assertThrows(IllegalArgumentException.class, () -> service.generate("  "));

    }

}

