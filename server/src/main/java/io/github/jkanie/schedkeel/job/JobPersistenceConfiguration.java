package io.github.jkanie.schedkeel.job;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.envers.repository.support.EnversRevisionRepositoryFactoryBean;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJpaRepositories(repositoryFactoryBeanClass = EnversRevisionRepositoryFactoryBean.class)
@EnableJpaAuditing(auditorAwareRef = "jobAuditor", dateTimeProviderRef = "jobAuditClock")
class JobPersistenceConfiguration {

    @Bean
    AuditorAware<String> jobAuditor() {
        return () -> Optional.of(CurrentUser.name().orElse("system"));
    }

    @Bean
    DateTimeProvider jobAuditClock(Clock clock) {
        return () -> Optional.of(Instant.now(clock));
    }
}
