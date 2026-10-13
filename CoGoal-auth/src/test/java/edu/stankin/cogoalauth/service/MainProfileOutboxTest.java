package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.MainProfileTask;
import edu.stankin.cogoalauth.db.entity.enums.OutboxStatus;
import edu.stankin.cogoalauth.db.repo.MainProfileTaskRepository;
import edu.stankin.cogoalauth.service.MainProfileOutbox.ClaimedTask;
import edu.stankin.cogoalauth.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MainProfileOutboxTest {

    @Mock
    private MainProfileTaskRepository repository;

    private MainProfileOutbox outbox;

    @BeforeEach
    void setUp() {
        outbox = new MainProfileOutbox(repository, TestData.properties(), Clock.fixed(TestData.NOW, ZoneOffset.UTC));
    }

    private MainProfileTask pendingTask(int attempts) {
        MainProfileTask task = new MainProfileTask();
        task.setId(UUID.randomUUID());
        task.setUserId(123L);
        task.setEmail("alice@example.com");
        task.setUsername("alice");
        task.setInitialName("alice");task.setRole("USER");task.setProfileVersion(1);task.setCreatedAt(TestData.NOW);
        task.setStatus(OutboxStatus.PENDING);
        task.setAttempts(attempts);
        task.setNextAttemptAt(TestData.NOW);
        return task;
    }

    @Test
    void enqueueWritesPendingTaskDueNow() {
        var user = TestData.user();

        outbox.enqueue(user);

        ArgumentCaptor<MainProfileTask> saved = ArgumentCaptor.forClass(MainProfileTask.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getUserId()).isEqualTo(user.getId());
        assertThat(saved.getValue().getEmail()).isEqualTo(user.getEmail());
        assertThat(saved.getValue().getUsername()).isEqualTo(user.getUsername());
        assertThat(saved.getValue().getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(saved.getValue().getNextAttemptAt()).isEqualTo(TestData.NOW);
    }

    @Test
    void claimCountsTheAttemptAndLeasesTheTask() {
        MainProfileTask task = pendingTask(2);
        given(repository.lockDue(TestData.NOW, 20)).willReturn(List.of(task));

        List<ClaimedTask> claimed = outbox.claimDue();

        assertThat(claimed).singleElement().satisfies(c -> {
            assertThat(c.id()).isEqualTo(task.getId());
            assertThat(c.attempt()).isEqualTo(3);
            assertThat(c.event().user().email()).isEqualTo("alice@example.com");
        });
        assertThat(task.getNextAttemptAt()).isEqualTo(TestData.NOW.plus(Duration.ofMinutes(1)));
    }

    @Test
    void retryIsScheduledWithGrowingDelay() {
        MainProfileTask task = pendingTask(3);
        given(repository.findById(task.getId())).willReturn(Optional.of(task));

        outbox.scheduleRetry(task.getId(), "timeout");

        assertThat(task.getNextAttemptAt()).isEqualTo(TestData.NOW.plusSeconds(40));
        assertThat(task.getLastError()).isEqualTo("timeout");
        assertThat(task.getStatus()).isEqualTo(OutboxStatus.PENDING);
    }

    @Test
    void backoffDoublesUpToTheMaximum() {
        assertThat(outbox.backoff(1)).isEqualTo(Duration.ofSeconds(10));
        assertThat(outbox.backoff(2)).isEqualTo(Duration.ofSeconds(20));
        assertThat(outbox.backoff(5)).isEqualTo(Duration.ofSeconds(160));
        assertThat(outbox.backoff(10)).isEqualTo(Duration.ofHours(1));
        assertThat(outbox.backoff(1000)).isEqualTo(Duration.ofHours(1));
    }

    @Test
    void doneAndNeedsAttentionAreFinal() {
        MainProfileTask done = pendingTask(1);
        MainProfileTask conflict = pendingTask(1);
        given(repository.findById(done.getId())).willReturn(Optional.of(done));
        given(repository.findById(conflict.getId())).willReturn(Optional.of(conflict));

        outbox.markDone(done.getId());
        outbox.markNeedsAttention(conflict.getId(), "x".repeat(2000));

        assertThat(done.getStatus()).isEqualTo(OutboxStatus.DONE);
        assertThat(done.getInitialName()).isNull();
        assertThat(done.getInitialAvatarUrl()).isNull();
        assertThat(conflict.getStatus()).isEqualTo(OutboxStatus.NEEDS_ATTENTION);
        assertThat(conflict.getLastError()).hasSize(1000);
    }
}
