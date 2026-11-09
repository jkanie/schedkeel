package io.github.jkanie.schedkeel;

import java.time.Clock;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;

/** Runs the API, the scheduler and the executor in 1 process. */
@SpringBootApplication
@EnableAsync
public class SchedkeelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SchedkeelApplication.class, args);
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    InstanceId instanceId(@Value("${schedkeel.instance-id:}") String configured) {
        String host = System.getenv().getOrDefault("HOSTNAME", "local");
        return new InstanceId(
                configured.isBlank() ? host + "-" + UUID.randomUUID().toString().substring(0, 8) : configured);
    }
}
