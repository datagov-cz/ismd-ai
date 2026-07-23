package cz.dia.ismd.assistant;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = "app.job-query.max-job-ids=1")
class JobQueryLimitIntegrationTests extends AssistantIntegrationTest {

    @Test
    void rejectsTooManyJobIdsForAllGetApis() throws Exception {
        String firstJobId = UUID.randomUUID().toString();
        String secondJobId = UUID.randomUUID().toString();

        for (String path : new String[]{
                "/legal-acts/class-suggestions-jobs",
                "/legal-acts/property-suggestions-jobs",
                "/legal-acts/relationship-suggestions-jobs"
        }) {
            mockMvc.perform(get(path)
                            .queryParam("jobIds", firstJobId, secondJobId)
                            .with(oidcAuthentication()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.detail", containsString("maximum is 1")));
        }
    }
}
