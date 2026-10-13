package edu.stankin.cogoalauth;
import edu.stankin.cogoalauth.service.*;
import edu.stankin.cogoalauth.db.repo.UserRepository;
import edu.stankin.cogoalauth.exception.*;
import edu.stankin.cogoalauth.support.PostgresIntegrationTest;
import edu.stankin.cogoalauth.web.dto.auth.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.mockito.ArgumentCaptor;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.springframework.test.context.TestPropertySource(properties="management.health.mail.enabled=false")
class AccountActionsIntegrationTest extends PostgresIntegrationTest {
    @Autowired AuthService auth;
    @Autowired AccountActions actions;
    @Autowired MailOutboxService mail;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @MockitoBean JavaMailSender sender;
    private String username;
    private String email;
    private TokenResponse registered;
    @BeforeEach void register() {
        username="u"+UUID.randomUUID().toString().replace("-", "").substring(0,20);
        email=username+"@example.com";
        registered=auth.register(new RegisterRequest(email,username,"Иван","password-1"));
    }
    @Test void eitherIdentifierFindsSameAccountAndSharesLockCounter() {
        assertThat(auth.login(new LoginRequest("  "+username.toUpperCase()+"  ","password-1")).user().id()).isEqualTo(registered.user().id());
        for(int i=0;i<5;i++) {
            String login=i%2==0?email:username;
            assertThatThrownBy(()->auth.login(new LoginRequest(login,"wrong-password"))).isInstanceOf(AuthenticationFailedException.class);
        }
        assertThatThrownBy(()->auth.login(new LoginRequest(email,"password-1"))).isInstanceOf(AccountLockedException.class);
        Instant until=users.findById(registered.user().id()).orElseThrow().getLockedUntil();
        assertThat(Duration.between(Instant.now(),until)).isBetween(Duration.ofMinutes(29),Duration.ofMinutes(31));
    }
    @Test void usernameChangeUpdatesLoginAndProducesVersionedSnapshot() {
        String next=username+"x";
        actions.changeUsername(registered.user().id(),next);
        assertThat(auth.login(new LoginRequest(next,"password-1")).user().username()).isEqualTo(next);
        assertThatThrownBy(()->auth.login(new LoginRequest(username,"password-1"))).isInstanceOf(AuthenticationFailedException.class);
        assertThat(jdbc.queryForObject("SELECT max(profile_version) FROM main_profile_outbox WHERE user_id=?",Long.class,registered.user().id())).isEqualTo(2);
    }
    private String lastLink() {
        mail.deliver();
        var capture=ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(sender,atLeastOnce()).send(capture.capture());
        String text=capture.getAllValues().stream().filter(m->Arrays.asList(m.getTo()).contains(email)).reduce((a,b)->b).orElseThrow().getText();
        return text.substring(text.indexOf("?token=")+7);
    }
    @Test void accountSchemaDoesNotContainProfileFieldsAndUpdatesDoNotPublishThem() {
        assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name IN ('name','avatar_url')",Integer.class)).isZero();
        actions.changeUsername(registered.user().id(),username+"x");
        assertThat(jdbc.queryForObject("SELECT initial_name FROM main_profile_outbox WHERE user_id=? AND profile_version=2",String.class,registered.user().id())).isNull();
    }
    @Test void passwordResetIsSingleUseAndRevokesRefreshTokens() {
        actions.forgot(email);
        String raw=lastLink();
        assertThat(jdbc.queryForObject("SELECT token_hash FROM auth_tokens WHERE user_id=? AND type='PASSWORD_RESET'",String.class,registered.user().id())).doesNotContain(raw);
        actions.reset(raw,"password-2");
        assertThat(auth.login(new LoginRequest(username,"password-2")).user().id()).isEqualTo(registered.user().id());
        assertThatThrownBy(()->auth.refresh(registered.refreshToken())).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(()->actions.reset(raw,"password-3")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(410));
    }
    @Test void concurrentResetCanConsumeTokenOnlyOnce() throws Exception {
        actions.forgot(email); String raw=lastLink();
        ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var outcomes=executor.invokeAll(List.<Callable<Integer>>of(()->reset(raw),()->reset(raw)));
            assertThat(List.of(outcomes.get(0).get(),outcomes.get(1).get())).containsExactlyInAnyOrder(204,410);
        } finally {executor.shutdownNow();}
    }
    private int reset(String raw) { try {actions.reset(raw,"password-2");return 204;}catch(ApiException e){return e.status();} }
    @Test void expiredLinkIsRejectedAndUnknownEmailDoesNotQueueMail() {
        actions.forgot(email);String raw=lastLink();
        jdbc.update("UPDATE auth_tokens SET expires_at=(CURRENT_TIMESTAMP AT TIME ZONE 'UTC') - interval '1 second' WHERE user_id=? AND type='PASSWORD_RESET'",registered.user().id());
        assertThatThrownBy(()->actions.reset(raw,"password-2")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(410));
        int before=jdbc.queryForObject("SELECT count(*) FROM mail_outbox",Integer.class);
        actions.forgot(UUID.randomUUID()+"@unknown.example");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox",Integer.class)).isEqualTo(before);
    }
    @Test void emailChangesOnlyAfterConfirmationAndUpdatesOutbox() {
        String original=email;
        email=username+"@new.example";
        actions.requestEmail(registered.user().id(),email,"password-1");
        assertThat(auth.me(registered.user().id()).email()).isEqualTo(original);
        String raw=lastLink();actions.confirmEmail(raw);
        assertThat(auth.login(new LoginRequest(email,"password-1")).user().email()).isEqualTo(email);
        assertThatThrownBy(()->auth.login(new LoginRequest(original,"password-1"))).isInstanceOf(AuthenticationFailedException.class);
        assertThatThrownBy(()->actions.confirmEmail(raw)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(410));
    }
    @Test void smtpFailureKeepsEncryptedMessageForRetry() {
        actions.forgot(email);
        doThrow(new org.springframework.mail.MailSendException("unavailable")).when(sender).send(any(SimpleMailMessage.class));
        mail.deliver();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox WHERE recipient=? AND attempts=1",Integer.class,email)).isEqualTo(1);
        jdbc.update("UPDATE mail_outbox SET next_attempt_at=? WHERE recipient=?",java.sql.Timestamp.from(Instant.now().minusSeconds(1)),email);
        doNothing().when(sender).send(any(SimpleMailMessage.class));mail.deliver();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM mail_outbox WHERE recipient=?",Integer.class,email)).isZero();
    }
}
