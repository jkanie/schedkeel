package io.github.jkanie.schedkeel;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.MockMvc;

/** Sets up jobs through the real API, signed in as admin. */
@Component
public class Api {

    private final MockMvc mvc;

    Api(MockMvc mvc) {
        this.mvc = mvc;
    }

    /** Creates a job from JSON fields, for example {@code "\"type\":\"LOG\",\"target\":\"hi\""}. */
    public void createJob(String name, String cron, String fields) throws Exception {
        String body = "{\"name\":\"" + name + "\",\"cron\":\"" + cron + "\"," + fields + "}";
        mvc.perform(post("/api/jobs")
                        .with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    public void trigger(String name) throws Exception {
        mvc.perform(post("/api/jobs/{name}/runs", name).with(httpBasic(TestData.ADMIN, TestData.ADMIN_PASSWORD)))
                .andExpect(status().isCreated());
    }
}
