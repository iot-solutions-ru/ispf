package com.ispf.server.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "ispf.security.rbac-enabled=true",
        "ispf.security.token-auth-enabled=true"
})
class WebConsoleSpaFallbackTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void servesEmbeddedIndexWithoutAuth() throws Exception {
        mockMvc.perform(get("/index.html").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("ispf-embedded-console")));
    }

    @Test
    void spaDeepLinkFallsBackToIndex() throws Exception {
        mockMvc.perform(get("/some/client/route").accept(MediaType.TEXT_HTML))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void apiRoutesStayProtected() throws Exception {
        mockMvc.perform(get("/api/v1/objects"))
                .andExpect(status().isForbidden());
    }
}
