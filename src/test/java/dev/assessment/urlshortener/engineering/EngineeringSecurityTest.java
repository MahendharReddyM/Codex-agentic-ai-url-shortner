package dev.assessment.urlshortener.engineering;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EngineeringSecurityTest {
    @Autowired MockMvc mvc;

    @Test
    void anonymousCannotStartEngineeringRun() throws Exception {
        mvc.perform(post("/api/v1/engineering/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Create a governed capability with executable tests.",
                                 "scenario":"GREENFIELD","repositoryPath":"."}
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void approverRoleCannotActAsSubmitter() throws Exception {
        mvc.perform(post("/api/v1/engineering/runs")
                        .with(httpBasic("approver", "local-approver-only"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"requirement":"Create a governed capability with executable tests.",
                                 "scenario":"GREENFIELD","repositoryPath":"."}
                                """))
                .andExpect(status().isForbidden());
    }
}
