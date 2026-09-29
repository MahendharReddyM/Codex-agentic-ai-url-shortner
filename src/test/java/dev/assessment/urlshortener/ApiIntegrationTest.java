package dev.assessment.urlshortener;

import static org.hamcrest.Matchers.endsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {
    @Autowired MockMvc mvc;

    @Test
    void exercisesUrlLifecycleAndAnalyticsThroughHttp() throws Exception {
        String body = "{\"url\":\"https://example.com/docs\",\"customAlias\":\"integration-demo\"}";

        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "integration-key")
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/r/integration-demo")))
                .andExpect(jsonPath("$.code").value("integration-demo"));

        mvc.perform(get("/r/integration-demo")
                        .header("Referer", "https://news.example/story")
                        .header("User-Agent", "Mozilla Mobile"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"));

        mvc.perform(get("/api/v1/links/integration-demo/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(1))
                .andExpect(jsonPath("$['topReferrers']['news.example']").value(1));

        mvc.perform(delete("/api/v1/links/integration-demo"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/r/integration-demo"))
                .andExpect(status().isGone());
    }

    @Test
    void returnsConsistentValidationEnvelope() throws Exception {
        mvc.perform(post("/api/v1/links")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"file:///etc/passwd\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSAFE_OR_INVALID_URL"))
                .andExpect(header().exists("X-Correlation-ID"));
    }
}
