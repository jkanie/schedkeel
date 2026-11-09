package io.github.jkanie.schedkeel.scheduling;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.jkanie.schedkeel.IntegrationTest;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;

@IntegrationTest
class LeaderLeaseTests {

    private static final String KEY = "schedkeel:test:leader";

    @Autowired
    StringRedisTemplate redis;

    @BeforeEach
    void clear() {
        redis.delete(KEY);
    }

    private LeaderLease lease(String holder, Duration ttl) {
        return new LeaderLease(redis, KEY, holder, ttl);
    }

    @Test
    void onlyOneInstanceHoldsTheLease() {
        LeaderLease a = lease("a", Duration.ofSeconds(30));
        LeaderLease b = lease("b", Duration.ofSeconds(30));

        assertThat(a.acquireOrRenew()).isTrue();
        assertThat(b.acquireOrRenew()).isFalse();
        assertThat(a.acquireOrRenew()).as("the holder renews").isTrue();
    }

    @Test
    void anotherInstanceTakesOverAfterRelease() {
        LeaderLease a = lease("a", Duration.ofSeconds(30));
        LeaderLease b = lease("b", Duration.ofSeconds(30));
        a.acquireOrRenew();

        b.release();
        assertThat(b.acquireOrRenew()).as("a non-holder cannot release").isFalse();

        a.release();
        assertThat(b.acquireOrRenew()).isTrue();
    }

    @Test
    void renewingResetsTheExpiry() {
        LeaderLease a = lease("a", Duration.ofSeconds(30));
        a.acquireOrRenew();
        redis.expire(KEY, Duration.ofSeconds(2));

        a.acquireOrRenew();
        assertThat(redis.getExpire(KEY)).isGreaterThan(20);
    }

    @Test
    void anExpiredLeaseIsFree() throws InterruptedException {
        lease("a", Duration.ofMillis(200)).acquireOrRenew();
        Thread.sleep(400);
        assertThat(lease("b", Duration.ofSeconds(30)).acquireOrRenew()).isTrue();
    }
}
