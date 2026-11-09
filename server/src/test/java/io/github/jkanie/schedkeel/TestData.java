package io.github.jkanie.schedkeel;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Resets shared state between tests. */
@Component
public class TestData {

    public static final String ADMIN = "admin";
    public static final String ADMIN_PASSWORD = "admin-test-password";
    public static final String VIEWER = "viewer";
    public static final String VIEWER_PASSWORD = "viewer-test-password";

    private final JdbcClient jdbc;
    private final StringRedisTemplate redis;

    TestData(JdbcClient jdbc, StringRedisTemplate redis) {
        this.jdbc = jdbc;
        this.redis = redis;
    }

    public void reset() {
        jdbc.sql("TRUNCATE runs, job_schedule, jobs_aud, revinfo, jobs, event_publication RESTART IDENTITY CASCADE")
                .update();
        redis.delete("schedkeel:scheduler:leader");
    }
}
