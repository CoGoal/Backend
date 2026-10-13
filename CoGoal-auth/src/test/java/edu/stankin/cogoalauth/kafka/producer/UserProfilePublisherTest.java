package edu.stankin.cogoalauth.kafka.producer;
import edu.stankin.cogoalauth.kafka.event.*;
import edu.stankin.cogoalauth.kafka.config.ProfileKafkaProperties;
import edu.stankin.cogoalauth.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.UUID;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import tools.jackson.databind.json.JsonMapper;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfilePublisherTest {
    @Mock KafkaTemplate<String,String> kafka;
    private final JsonMapper mapper=JsonMapper.builder().findAndAddModules().build();
    private final ProfileKafkaProperties properties=new ProfileKafkaProperties("profiles",Duration.ofMillis(30),3);
    private UserProfileEvent event() { return UserProfileEvent.of(UUID.randomUUID(),TestData.NOW,new UserProfileSnapshot(42L,"a@example.com","alice")); }
    @Test void publishesCompleteSnapshotWithStableIdAndUserPartitionKey() {
        var event=event();when(kafka.send(eq("profiles"),eq("42"),anyString())).thenReturn(CompletableFuture.completedFuture(null));
        new UserProfilePublisher(kafka,mapper,properties).publish(event);
        var payload=ArgumentCaptor.forClass(String.class);verify(kafka).send(eq("profiles"),eq("42"),payload.capture());
        assertThat(mapper.readValue(payload.getValue(),UserProfileEvent.class)).isEqualTo(event);
        assertThat(payload.getValue()).doesNotContain("passwordHash","refreshToken","accessToken");
    }
    @Test void brokerFailureIsReportedToOutbox() {
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
        assertThatThrownBy(()->new UserProfilePublisher(kafka,mapper,properties).publish(event()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not acknowledge");
    }
    @Test void missingAcknowledgementTimesOutWithoutMarkingPublicationSuccessful() {
        when(kafka.send(anyString(),anyString(),anyString())).thenReturn(new CompletableFuture<>());
        assertThatThrownBy(()->new UserProfilePublisher(kafka,mapper,properties).publish(event()))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("did not acknowledge");
    }
}
