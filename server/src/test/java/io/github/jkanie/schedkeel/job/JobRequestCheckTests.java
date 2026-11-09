package io.github.jkanie.schedkeel.job;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class JobRequestCheckTests {

    private static JobRequest request(String cron, String zone, JobType type, String target) {
        return new JobRequest("nightly", null, cron, zone, type, target, null, null, null, null, null, null);
    }

    @Test
    void acceptsAValidRequest() {
        assertThatCode(() ->
                        JobService.check(request("0 0 2 * * *", "Europe/Paris", JobType.HTTP, "https://example.org")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAFiveFieldCron() {
        assertThatThrownBy(() -> JobService.check(request("0 2 * * *", null, JobType.LOG, "x")))
                .isInstanceOf(InvalidJobException.class)
                .hasMessageContaining("6 fields");
    }

    @Test
    void refusesAnUnknownZone() {
        assertThatThrownBy(() -> JobService.check(request("0 0 2 * * *", "Mars/Olympus", JobType.LOG, "x")))
                .isInstanceOf(InvalidJobException.class)
                .hasMessageContaining("time zone");
    }

    @Test
    void refusesAnHttpJobWithoutAUrl() {
        assertThatThrownBy(() -> JobService.check(request("0 0 2 * * *", null, JobType.HTTP, "example.org")))
                .isInstanceOf(InvalidJobException.class)
                .hasMessageContaining("http://");
    }
}
