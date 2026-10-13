package edu.stankin.cogoalauth.kafka.producer;
import edu.stankin.cogoalauth.kafka.event.*;
import edu.stankin.cogoalauth.service.MainProfileOutbox;
import edu.stankin.cogoalauth.service.MainProfileOutbox.ClaimedTask;
import edu.stankin.cogoalauth.support.TestData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfileOutboxPublisherTest {
    @Mock MainProfileOutbox outbox;
    @Mock UserProfilePublisher publisher;
    @InjectMocks ProfileOutboxPublisher worker;
    private ClaimedTask task(Long userId) {
        UUID id=UUID.randomUUID();
        return new ClaimedTask(id,1,UserProfileEvent.of(id,TestData.NOW,new UserProfileSnapshot(userId,"a@example.com","alice")));
    }
    @Test void acknowledgedPublicationMarksTaskDone() {
        var task=task(1L);when(outbox.claimDue()).thenReturn(List.of(task));
        assertThat(worker.processDue()).isEqualTo(1);
        InOrder order=inOrder(publisher,outbox);order.verify(publisher).publish(task.event());order.verify(outbox).markDone(task.id());
    }
    @Test void failedPublicationRemainsPendingForRetryAndOtherTasksContinue() {
        var failed=task(1L);var successful=task(2L);when(outbox.claimDue()).thenReturn(List.of(failed,successful));
        doThrow(new IllegalStateException("broker unavailable")).when(publisher).publish(failed.event());
        assertThat(worker.processDue()).isEqualTo(2);
        verify(outbox).scheduleRetry(eq(failed.id()),contains("Kafka publication failed"));
        verify(outbox,never()).markDone(failed.id());verify(outbox).markDone(successful.id());
    }
    @Test void pollingFailureDoesNotStopFutureScheduledRuns() {
        when(outbox.claimDue()).thenThrow(new IllegalStateException("database unavailable"));
        assertThatCode(worker::run).doesNotThrowAnyException();verifyNoInteractions(publisher);
    }
}
