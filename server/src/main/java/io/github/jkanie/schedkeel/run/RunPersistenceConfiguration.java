package io.github.jkanie.schedkeel.run;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories;

@Configuration(proxyBeanMethods = false)
@EnableJdbcRepositories
class RunPersistenceConfiguration {}
