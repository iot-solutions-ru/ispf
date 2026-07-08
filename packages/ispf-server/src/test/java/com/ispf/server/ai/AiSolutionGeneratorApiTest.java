package com.ispf.server.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.security.test.context.support.WithMockUser;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AiSolutionGeneratorApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "admin")
    void scadaPromptReturnsBlueprintDraft() throws Exception {
        mockMvc.perform(post("/api/v1/ai/solutions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"SCADA tank farm with 2 pumps and high pressure alert"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.mode").value("stub"))
                .andExpect(jsonPath("$.domain").value("scada"))
                .andExpect(jsonPath("$.blueprintDraft.domain").value("scada"))
                .andExpect(jsonPath("$.blueprintDraft.specBrief.entities").isArray())
                .andExpect(jsonPath("$.blueprintDraft.suggestedArtifacts.dashboards").isArray())
                .andExpect(jsonPath("$.blueprintDraft.referenceBundle.appId").value("simulator"));
    }

    @Test
    @WithMockUser(roles = "admin")
    void mesPromptReturnsMesDomainDraft() throws Exception {
        mockMvc.perform(post("/api/v1/ai/solutions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"MES dispatch work orders with OEE dashboard"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value("mes"))
                .andExpect(jsonPath("$.blueprintDraft.referenceBundle.appId").value("mes-reference"));
    }

    @Test
    @WithMockUser(roles = "admin")
    void hvacPromptReturnsHvacDomainDraft() throws Exception {
        mockMvc.perform(post("/api/v1/ai/solutions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"HVAC building comfort zones with AHU setpoints"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value("hvac"))
                .andExpect(jsonPath("$.blueprintDraft.referenceBundle.appId").value("building-hvac"));
    }

    @Test
    @WithMockUser(roles = "admin")
    void oilGasPromptReturnsOilGasDomainDraft() throws Exception {
        mockMvc.perform(post("/api/v1/ai/solutions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Oil and gas upstream pump station with tank farm"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value("oil-gas"))
                .andExpect(jsonPath("$.blueprintDraft.referenceBundle.appId").value("simulator"));
    }

    @Test
    @WithMockUser(roles = "admin")
    void energyPromptReturnsMiniTecReferenceBundle() throws Exception {
        mockMvc.perform(post("/api/v1/ai/solutions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prompt":"Thermal power plant energy turbine reporting"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.domain").value("energy"))
                .andExpect(jsonPath("$.blueprintDraft.referenceBundle.appId").value("mini-tec"));
    }

    @Test
    @WithMockUser(roles = "admin")
    void blankPromptIsRejected() throws Exception {
        mockMvc.perform(post("/api/v1/ai/solutions/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prompt\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
