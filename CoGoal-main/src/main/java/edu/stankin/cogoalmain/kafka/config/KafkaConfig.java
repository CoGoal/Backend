package edu.stankin.cogoalmain.kafka.config;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.FixedBackOff;
import org.springframework.dao.DataIntegrityViolationException;

@Configuration
public class KafkaConfig {
    @Bean NewTopic userProfilesTopic(@Value("${app.kafka.profile-topic}") String topic,
                                     @Value("${app.kafka.partitions}") int partitions) {
        return TopicBuilder.name(topic).partitions(partitions).replicas(1).build();
    }
    @Bean NewTopic userProfilesDeadLetterTopic(@Value("${app.kafka.profile-topic}") String topic,
                                               @Value("${app.kafka.partitions}") int partitions) {
        return TopicBuilder.name(topic+".DLT").partitions(partitions).replicas(1).build();
    }
    @Bean ConcurrentKafkaListenerContainerFactory<String,String> profileKafkaListenerContainerFactory(
            ConsumerFactory<String,String> consumers,KafkaTemplate<String,String> template,
            @Value("${app.kafka.profile-topic}") String topic,@Value("${app.kafka.partitions}") int partitions,
            @Value("${spring.kafka.listener.auto-startup:true}") boolean autoStartup) {
        var recoverer=new DeadLetterPublishingRecoverer(template,(record,exception)->new TopicPartition(topic+".DLT",record.partition()));
        recoverer.setFailIfSendResultIsError(true);
        var errors=new DefaultErrorHandler(recoverer,new FixedBackOff(1000L,2L));
        errors.addNotRetryableExceptions(IllegalArgumentException.class,DataIntegrityViolationException.class);
        var factory=new ConcurrentKafkaListenerContainerFactory<String,String>();
        factory.setConsumerFactory(consumers);factory.setConcurrency(partitions);factory.setAutoStartup(autoStartup);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.RECORD);
        factory.setCommonErrorHandler(errors);
        return factory;
    }
}
