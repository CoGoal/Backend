package edu.stankin.cogoalauth.kafka.producer;
import edu.stankin.cogoalauth.kafka.config.ProfileKafkaProperties;
import edu.stankin.cogoalauth.kafka.event.UserProfileEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import java.util.concurrent.TimeUnit;

@Component
public class UserProfilePublisher {
    private final KafkaTemplate<String,String> kafka;
    private final ObjectMapper mapper;
    private final ProfileKafkaProperties properties;
    public UserProfilePublisher(KafkaTemplate<String,String> kafka,ObjectMapper mapper,ProfileKafkaProperties properties) {
        this.kafka=kafka;this.mapper=mapper;this.properties=properties;
    }
    /** Return only after the broker acknowledges the event; the outbox may then mark it sent. */
    public void publish(UserProfileEvent event) {
        String json=mapper.writeValueAsString(event);
        try {
            kafka.send(properties.profileTopic(),event.user().id().toString(),json)
                 .get(properties.sendTimeout().toMillis(),TimeUnit.MILLISECONDS);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Kafka publication interrupted",exception);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException exception) {
            throw new IllegalStateException("Kafka did not acknowledge the profile event",exception);
        }
    }
}
