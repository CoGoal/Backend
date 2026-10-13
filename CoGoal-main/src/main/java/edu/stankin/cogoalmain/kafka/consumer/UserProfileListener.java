package edu.stankin.cogoalmain.kafka.consumer;
import edu.stankin.cogoalmain.kafka.event.UserProfileEvent;
import edu.stankin.cogoalmain.service.ProfileService;
import jakarta.validation.Validator;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class UserProfileListener {
    private final ObjectMapper mapper;
    private final Validator validator;
    private final ProfileService profiles;
    public UserProfileListener(ObjectMapper mapper,Validator validator,ProfileService profiles) {
        this.mapper=mapper;this.validator=validator;this.profiles=profiles;
    }
    @KafkaListener(topics="${app.kafka.profile-topic}",groupId="${app.kafka.profile-group}",
                   containerFactory="profileKafkaListenerContainerFactory")
    public void onEvent(ConsumerRecord<String,String> record) {
        UserProfileEvent event;
        try { event=mapper.readValue(record.value(),UserProfileEvent.class); }
        catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid profile event JSON",exception); }
        if (event==null || !validator.validate(event).isEmpty()) throw new IllegalArgumentException("Invalid profile event schema");
        if (!event.user().id().toString().equals(record.key())) throw new IllegalArgumentException("Profile event key must match user id");
        if (event.schemaVersion()==2) {
            boolean registration = "UserRegistered".equals(event.eventType());
            if (registration && (event.user().version()!=1 || event.user().name()==null))
                throw new IllegalArgumentException("Registration must contain the initial profile name");
            if (!registration && (event.user().name()!=null || event.user().avatarUrl()!=null))
                throw new IllegalArgumentException("Account updates must not change profile fields");
        }
        // The service transaction commits before the listener returns and Kafka acknowledges the record.
        profiles.sync(event.user());
    }
}
