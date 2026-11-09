package io.github.jkanie.schedkeel.scheduling;

import java.time.Duration;
import java.util.List;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

/**
 * A Redis lease that at most 1 instance holds at a time. The holder renews it
 * on every tick. When the holder stops, the key expires and another instance
 * takes over.
 */
class LeaderLease {

    private static final RedisScript<Long> RENEW = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('pexpire', KEYS[1], ARGV[2])
            end
            return 0
            """, Long.class);

    private static final RedisScript<Long> RELEASE = RedisScript.of("""
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final String key;
    private final String holder;
    private final Duration ttl;

    LeaderLease(StringRedisTemplate redis, String key, String holder, Duration ttl) {
        this.redis = redis;
        this.key = key;
        this.holder = holder;
        this.ttl = ttl;
    }

    /** Takes the lease when free, or renews it when this instance holds it. */
    boolean acquireOrRenew() {
        Boolean taken = redis.opsForValue().setIfAbsent(key, holder, ttl);
        if (Boolean.TRUE.equals(taken)) {
            return true;
        }
        Long renewed = redis.execute(RENEW, List.of(key), holder, String.valueOf(ttl.toMillis()));
        return renewed != null && renewed == 1L;
    }

    void release() {
        redis.execute(RELEASE, List.of(key), holder);
    }

    String holder() {
        return holder;
    }
}
