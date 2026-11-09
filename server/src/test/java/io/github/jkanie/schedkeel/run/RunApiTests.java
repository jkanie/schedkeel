package io.github.jkanie.schedkeel.run;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.request.RequestDocumentation.parameterWithName;
import static org.springframework.restdocs.request.RequestDocumentation.queryParameters;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jkanie.schedkeel.Api;
import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class RunApiTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    Api api;

    @Autowired
    TestData data;

    @BeforeEach
    void reset() {
        data.reset();
    }

    @Test
    void triggersARunNow() throws Exception {
        api.createJob("hello", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"hi\"");
        mvc.perform(post("/api/jobs/hello/runs").with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.trigger").value("MANUAL"))
                .andExpect(jsonPath("$.attempt").value(1))
                .andDo(document("trigger-run", preprocessResponse(prettyPrint())));
    }

    @Test
    void listsRunsNewestFirstAndFiltersByJob() throws Exception {
        api.createJob("hello", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"hi\",\"concurrency\":\"ALLOW\"");
        api.createJob("other", "0 0 0 1 1 *", "\"type\":\"LOG\",\"target\":\"hi\"");
        api.trigger("hello");
        api.trigger("hello");
        api.trigger("other");

        mvc.perform(get("/api/runs").param("job", "hello").with(httpBasic(TestData.VIEWER, TestData.VIEWER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.runs", hasSize(2)))
                .andExpect(jsonPath("$._embedded.runs[0].id").value(2))
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andDo(document(
                        "list-runs",
                        preprocessResponse(prettyPrint()),
                        queryParameters(
                                parameterWithName("job").optional().description("Only runs of this job"),
                                parameterWithName("status").optional().description("Only runs with this status"),
                                parameterWithName("page").optional().description("Page number, from 0"),
                                parameterWithName("size").optional().description("Page size. Default 20"),
                                parameterWithName("sort").optional().description("Default id,desc"))));

        mvc.perform(get("/api/runs")
                        .param("status", "QUEUED")
                        .with(httpBasic(TestData.VIEWER, TestData.VIEWER_PASSWORD)))
                .andExpect(jsonPath("$._embedded.runs", hasSize(3)));
    }

    @Test
    void returns404ForAnUnknownRunOrJob() throws Exception {
        mvc.perform(get("/api/runs/999").with(httpBasic(TestData.VIEWER, TestData.VIEWER_PASSWORD)))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/jobs/missing/runs").with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)))
                .andExpect(status().isNotFound());
    }

    @Test
    void theApiRootLinksToEverything() throws Exception {
        mvc.perform(get("/api").with(httpBasic(TestData.VIEWER, TestData.VIEWER_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._links.jobs.href").value(containsString("/api/jobs")))
                .andExpect(jsonPath("$._links.runs.templated").value(true))
                .andExpect(jsonPath("$._links.me.href").exists());
    }
}
