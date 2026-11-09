package io.github.jkanie.schedkeel.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.github.jkanie.schedkeel.IntegrationTest;
import io.github.jkanie.schedkeel.TestData;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The dashboard's sign-in flow, step by step as a browser does it: read the
 * XSRF-TOKEN cookie, echo it in the X-XSRF-TOKEN header, keep the SESSION
 * cookie.
 */
@IntegrationTest
class SessionTests {

    @Autowired
    MockMvc mvc;

    @Autowired
    StringRedisTemplate redis;

    private Cookie csrfCookie() throws Exception {
        return mvc.perform(get("/api/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
    }

    private Cookie signIn(String user, String password) throws Exception {
        Cookie csrf = csrfCookie();
        MvcResult result = mvc.perform(post("/api/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .param("username", user)
                        .param("password", password))
                .andExpect(status().isNoContent())
                .andExpect(cookie().exists("SESSION"))
                .andReturn();
        return result.getResponse().getCookie("SESSION");
    }

    @Test
    void theFirstResponseHandsTheDashboardACsrfCookieButNoSession() throws Exception {
        mvc.perform(get("/api/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false))
                .andExpect(cookie().doesNotExist("SESSION"));
    }

    @Test
    void reloadingADashboardPageServesTheDashboard() throws Exception {
        for (String path : new String[] {"/login", "/jobs", "/jobs/nightly-report", "/runs"}) {
            mvc.perform(get(path)).andExpect(status().isOk()).andExpect(forwardedUrl("/index.html"));
        }
    }

    @Test
    void signInKeepsTheSessionInRedis() throws Exception {
        Cookie session = signIn(TestData.VIEWER, TestData.VIEWER_PASSWORD);

        mvc.perform(get("/api/me").cookie(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("viewer"))
                .andExpect(jsonPath("$.roles[0]").value("VIEWER"))
                .andExpect(jsonPath("$.canChange").value(false));
        assertThat(redis.keys("spring:session:sessions:*")).isNotEmpty();
    }

    @Test
    void signOutEndsTheSession() throws Exception {
        Cookie session = signIn(TestData.ADMIN, TestData.ADMIN_PASSWORD);
        mvc.perform(get("/api/me").cookie(session))
                .andExpect(jsonPath("$.canChange").value(true));

        Cookie csrf = csrfCookie();
        mvc.perform(post("/api/logout").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void aWrongPasswordGets401() throws Exception {
        Cookie csrf = csrfCookie();
        mvc.perform(post("/api/login")
                        .cookie(csrf)
                        .header("X-XSRF-TOKEN", csrf.getValue())
                        .param("username", "admin")
                        .param("password", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void aPostWithoutTheCsrfHeaderIsRefused() throws Exception {
        Cookie session = signIn(TestData.ADMIN, TestData.ADMIN_PASSWORD);
        Cookie csrf = csrfCookie();
        mvc.perform(post("/api/jobs/any/pause").cookie(session, csrf)).andExpect(status().isForbidden());
        mvc.perform(post("/api/jobs/any/pause").cookie(session, csrf).header("X-XSRF-TOKEN", "forged"))
                .andExpect(status().isForbidden());
        // With the right header the request gets through to the API, which has no such job.
        mvc.perform(post("/api/jobs/any/pause").cookie(session, csrf).header("X-XSRF-TOKEN", csrf.getValue()))
                .andExpect(status().isNotFound());
    }
}
