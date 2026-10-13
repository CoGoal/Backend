package edu.stankin.cogoalauth.kafka.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.kafka.config.TopicBuilder;

@Configuration @EnableConfigurationProperties(ProfileKafkaProperties.class)
public class KafkaConfig {
    @Bean NewTopic userProfilesTopic(ProfileKafkaProperties properties) {
        return TopicBuilder.name(properties.profileTopic()).partitions(properties.partitions()).replicas(1).build();
    }
}
