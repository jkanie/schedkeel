package io.github.jkanie.schedkeel.job;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel;
import static org.springframework.restdocs.hypermedia.HypermediaDocumentation.links;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;
import static org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath;
import static org.springframework.restdocs.payload.PayloadDocumentation.requestFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.restdocs.payload.FieldDescriptor;
import org.springframework.restdocs.payload.JsonFieldType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@IntegrationTest
class JobApiTests {

    private static final String NIGHTLY = """
            {"name": "nightly-report", "description": "Builds the nightly sales report",
             "cron": "0 0 2 * * *", "timeZone": "America/Whitehorse",
             "type": "HTTP", "target": "https://reports.example.org/build",
             "maxRetries": 2, "retryBackoffSeconds": 60, "timeoutSeconds": 600}
            """;

    private static final FieldDescriptor[] REQUEST = {
        fieldWithPath("name").description("Unique name: lower-case letters, digits and hyphens"),
        fieldWithPath("description").optional().description("What the job is for"),
        fieldWithPath("cron").description("6-field cron: second, minute, hour, day of month, month, day of week"),
        fieldWithPath("timeZone").optional().description("Zone the cron is read in. Default UTC"),
        fieldWithPath("type").description("LOG, HTTP, KAFKA or BATCH"),
        fieldWithPath("target").description("URL, topic or batch job name, by type"),
        fieldWithPath("payload").optional().type(JsonFieldType.STRING).description("Body, message or log text"),
        fieldWithPath("enabled").optional().type(JsonFieldType.BOOLEAN).description("Default true"),
        fieldWithPath("concurrency")
                .optional()
                .type(JsonFieldType.STRING)
                .description("ALLOW or FORBID. Default FORBID"),
        fieldWithPath("maxRetries").optional().description("Retries after a failed first attempt. Default 0"),
        fieldWithPath("retryBackoffSeconds")
                .optional()
                .description("Wait before the first retry; doubles after each. Default 30"),
        fieldWithPath("timeoutSeconds").optional().description("Longest a run may take. Default 300"),
    };

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData data;

    @BeforeEach
    void reset() {
        data.reset();
    }

    private static RequestPostProcessor admin() {
        return httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD);
    }

    private static RequestPostProcessor viewer() {
        return httpBasic(TestData.VIEWER, TestData.VIEWER_PASSWORD);
    }

    private void createNightly() throws Exception {
        mvc.perform(post("/api/jobs")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NIGHTLY))
                .andExpect(status().isCreated());
    }

    @Test
    void createsAJob() throws Exception {
        mvc.perform(post("/api/jobs")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NIGHTLY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "http://localhost:8080/api/jobs/nightly-report"))
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.concurrency").value("FORBID"))
                .andExpect(jsonPath("$.createdBy").value("admin"))
                .andExpect(jsonPath("$._links.trigger.href").value(containsString("/api/jobs/nightly-report/runs")))
                .andDo(document(
                        "create-job",
                        preprocessResponse(prettyPrint()),
                        requestFields(REQUEST),
                        links(
                                linkWithRel("self").description("This job"),
                                linkWithRel("history").description("Every saved version of this job"),
                                linkWithRel("jobs").description("All jobs"),
                                linkWithRel("pause").description("Stops the schedule (present while enabled)"),
                                linkWithRel("runs").description("Runs of this job, newest first"),
                                linkWithRel("trigger").description("POST here to run the job now"))));
    }

    @Test
    void listsJobsByName() throws Exception {
        createNightly();
        mvc.perform(
                        post("/api/jobs")
                                .with(admin())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        "{\"name\":\"a-first\",\"cron\":\"0 * * * * *\",\"type\":\"LOG\",\"target\":\"hello\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/jobs").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.jobs[*].name", contains("a-first", "nightly-report")))
                .andDo(document(
                        "list-jobs",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                subsectionWithPath("_embedded.jobs").description("Jobs, sorted by name"),
                                subsectionWithPath("_links").description("Links"))));
    }

    @Test
    void keepsEveryVersionWithWhoChangedIt() throws Exception {
        createNightly();
        mvc.perform(put("/api/jobs/nightly-report")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NIGHTLY.replace("0 0 2 * * *", "0 30 3 * * *")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cron").value("0 30 3 * * *"));
        mvc.perform(post("/api/jobs/nightly-report/pause").with(admin()))
                .andExpect(jsonPath("$.enabled").value(false))
                .andExpect(jsonPath("$._links.resume.href").exists())
                .andExpect(jsonPath("$._links.pause").doesNotExist());

        mvc.perform(get("/api/jobs/nightly-report/history").with(viewer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$._embedded.revisions", hasSize(3)))
                .andExpect(jsonPath("$._embedded.revisions[0].change").value("UPDATE"))
                .andExpect(jsonPath("$._embedded.revisions[0].job.enabled").value(false))
                .andExpect(jsonPath("$._embedded.revisions[1].job.cron").value("0 30 3 * * *"))
                .andExpect(jsonPath("$._embedded.revisions[2].change").value("INSERT"))
                .andExpect(jsonPath("$._embedded.revisions[2].job.cron").value("0 0 2 * * *"))
                .andExpect(jsonPath("$._embedded.revisions[*].changedBy", contains("admin", "admin", "admin")))
                .andDo(document(
                        "job-history",
                        preprocessResponse(prettyPrint()),
                        responseFields(
                                fieldWithPath("_embedded.revisions[].revision").description("Revision number"),
                                fieldWithPath("_embedded.revisions[].change").description("INSERT, UPDATE or DELETE"),
                                fieldWithPath("_embedded.revisions[].changedAt").description("When"),
                                fieldWithPath("_embedded.revisions[].changedBy").description("Who"),
                                subsectionWithPath("_embedded.revisions[].job").description("The job as saved"),
                                subsectionWithPath("_links").description("Links"))));
    }

    @Test
    void refusesABadCronWithAProblemDetail() throws Exception {
        mvc.perform(post("/api/jobs")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NIGHTLY.replace("0 0 2 * * *", "0 2 * * *")))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.detail").value(containsString("6 fields")));
    }

    @Test
    void refusesAMissingFieldWithAProblemDetail() throws Exception {
        mvc.perform(post("/api/jobs")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"x\",\"cron\":\"0 * * * * *\",\"type\":\"LOG\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(header().string("Content-Type", "application/problem+json"));
    }

    @Test
    void refusesADuplicateName() throws Exception {
        createNightly();
        mvc.perform(post("/api/jobs")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NIGHTLY))
                .andExpect(status().isConflict());
    }

    @Test
    void returns404ForAnUnknownJob() throws Exception {
        mvc.perform(get("/api/jobs/missing").with(viewer()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("No job is named 'missing'."));
    }

    @Test
    void deletesAJob() throws Exception {
        createNightly();
        mvc.perform(delete("/api/jobs/nightly-report").with(admin())).andExpect(status().isNoContent());
        mvc.perform(get("/api/jobs/nightly-report").with(admin())).andExpect(status().isNotFound());
    }

    @Test
    void viewersMayReadButNotChange() throws Exception {
        mvc.perform(get("/api/jobs").with(viewer())).andExpect(status().isOk());
        mvc.perform(post("/api/jobs")
                        .with(viewer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NIGHTLY))
                .andExpect(status().isForbidden());
    }

    @Test
    void anonymousCallersGet401() throws Exception {
        mvc.perform(get("/api/jobs")).andExpect(status().isUnauthorized());
    }
}
