package io.github.jkanie.schedkeel.job;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void acceptsAnAmqpTargetWithOrWithoutAnExchange() {
        assertThatCode(() -> JobService.check(request("0 0 2 * * *", null, JobType.AMQP, "billing/invoice.due")))
                .doesNotThrowAnyException();
        assertThatCode(() -> JobService.check(request("0 0 2 * * *", null, JobType.AMQP, "/orders")))
                .doesNotThrowAnyException();
        assertThatCode(() -> JobService.check(request("0 0 2 * * *", null, JobType.AMQP, "billing")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAnAmqpTargetWithNeitherExchangeNorKey() {
        assertThatThrownBy(() -> JobService.check(request("0 0 2 * * *", null, JobType.AMQP, "/")))
                .isInstanceOf(InvalidJobException.class)
                .hasMessageContaining("exchange/routing-key");
    }

    @Test
    void readsPulsarTopicsTheWayThePulsarClientDoes() {
        assertThat(PulsarTarget.parse("orders").topic()).isEqualTo("persistent://public/default/orders");
        assertThat(PulsarTarget.parse(" billing/jobs/invoice.due ").topic())
                .isEqualTo("persistent://billing/jobs/invoice.due");
        assertThat(PulsarTarget.parse("non-persistent://public/default/ticks").topic())
                .isEqualTo("non-persistent://public/default/ticks");
        assertThatCode(() -> JobService.check(request("0 0 2 * * *", null, JobType.PULSAR, "orders")))
                .doesNotThrowAnyException();
    }

    @Test
    void refusesAPulsarTargetThatIsNotATopic() {
        for (String target : new String[] {"", "a/b", "a b", "kafka://x/y/z", "persistent://a/b", "a/b/c/d"}) {
            assertThatThrownBy(() -> JobService.check(request("0 0 2 * * *", null, JobType.PULSAR, target)))
                    .as(target)
                    .isInstanceOf(InvalidJobException.class)
                    .hasMessageContaining("is not a Pulsar topic");
        }
    }

    @Test
    void splitsAnAmqpTargetAtTheFirstSlash() {
        assertThat(AmqpTarget.parse("billing/invoice/due")).isEqualTo(new AmqpTarget("billing", "invoice/due"));
        assertThat(AmqpTarget.parse("/orders").describe()).isEqualTo("the default exchange, routing key 'orders'");
    }
}
