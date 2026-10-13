package edu.stankin.cogoalmain.kafka.consumer;
import edu.stankin.cogoalmain.kafka.event.UserProfileEvent;
import edu.stankin.cogoalmain.pojo.dto.ProfileSnapshot;
import edu.stankin.cogoalmain.db.repo.UserProfileRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import tools.jackson.databind.ObjectMapper;
import java.util.UUID;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
class UserProfileListenerTest {
    private static final AtomicLong IDS=new AtomicLong(10000);
    @Autowired UserProfileListener listener;
    @Autowired UserProfileRepository profiles;
    @Autowired ObjectMapper mapper;
    private UserProfileEvent event(Long id,long version,String name) {
        return new UserProfileEvent(UUID.randomUUID(),1,version==1?"UserRegistered":"UserProfileUpdated",Instant.now(),
            new ProfileSnapshot(id,"u"+id+"@example.com","u"+id,name,null,"USER",version,null));
    }
    private ConsumerRecord<String,String> record(UserProfileEvent event) {
        return new ConsumerRecord<>("profiles",0,0,event.user().id().toString(),mapper.writeValueAsString(event));
    }
    @Test void duplicatesAndOlderEventsDoNotOverwriteLatestProfileOrAwardCoinsAgain() {
        Long id=IDS.incrementAndGet();var first=event(id,1,"Имя");listener.onEvent(record(first));
        var stored=profiles.findById(id).orElseThrow();stored.setCoins(47);profiles.saveAndFlush(stored);
        listener.onEvent(record(first));listener.onEvent(record(event(id,3,"Новое имя")));listener.onEvent(record(event(id,2,"Старое имя")));
        var latest=profiles.findById(id).orElseThrow();assertThat(latest.getName()).isEqualTo("Имя");
        assertThat(latest.getCoins()).isEqualTo(47);assertThat(latest.getProfileVersion()).isEqualTo(3);
    }
    @Test void malformedJsonAndUnsupportedSchemaAreRejectedBeforeDatabaseWrite() {
        Long id=IDS.incrementAndGet();var valid=event(id,1,"Имя");
        assertThatThrownBy(()->listener.onEvent(new ConsumerRecord<>("profiles",0,0,id.toString(),"not-json"))).isInstanceOf(IllegalArgumentException.class);
        var invalid=new UserProfileEvent(valid.eventId(),3,valid.eventType(),valid.occurredAt(),valid.user());
        assertThatThrownBy(()->listener.onEvent(record(invalid))).isInstanceOf(IllegalArgumentException.class);
        assertThat(profiles.findById(id)).isEmpty();
    }
    @Test void partitionKeyMustMatchNumericAccountId() {
        Long id=IDS.incrementAndGet();var event=event(id,1,"Имя");
        assertThatThrownBy(()->listener.onEvent(new ConsumerRecord<>("profiles",0,0,"different",mapper.writeValueAsString(event))))
            .isInstanceOf(IllegalArgumentException.class);assertThat(profiles.findById(id)).isEmpty();
    }
    @Test void schemaTwoAccountUpdateOmitsProfileFieldsAndRejectsAttemptsToChangeThem() {
        Long id=IDS.incrementAndGet(); var registration=event(id,1,"Имя"); listener.onEvent(record(registration));
        var update=new UserProfileEvent(UUID.randomUUID(),2,"UserProfileUpdated",Instant.now(),
            new ProfileSnapshot(id,"next"+id+"@example.com","next"+id,null,null,"USER",2,null));
        listener.onEvent(record(update));
        assertThat(profiles.findById(id).orElseThrow().getName()).isEqualTo("Имя");
        assertThat(profiles.findById(id).orElseThrow().getUsername()).isEqualTo("next"+id);
        var invalid=new UserProfileEvent(UUID.randomUUID(),2,"UserProfileUpdated",Instant.now(),
            new ProfileSnapshot(id,"next"+id+"@example.com","next"+id,"Чужое имя",null,"USER",3,null));
        assertThatThrownBy(()->listener.onEvent(record(invalid))).isInstanceOf(IllegalArgumentException.class);
    }
}
