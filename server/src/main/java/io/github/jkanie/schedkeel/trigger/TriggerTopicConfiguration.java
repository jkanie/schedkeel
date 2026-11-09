package io.github.jkanie.schedkeel.trigger;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration(proxyBeanMethods = false)
class TriggerTopicConfiguration {

    /** Creates the trigger topic at start-up when it does not exist yet. */
    @Bean
    NewTopic triggerTopic(@Value("${schedkeel.kafka.trigger-topic}") String name) {
        return TopicBuilder.name(name).partitions(3).build();
    }
}
