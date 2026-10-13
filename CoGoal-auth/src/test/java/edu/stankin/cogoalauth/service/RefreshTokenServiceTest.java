package edu.stankin.cogoalauth.service;

import edu.stankin.cogoalauth.db.entity.RefreshToken;
import edu.stankin.cogoalauth.db.entity.User;
import edu.stankin.cogoalauth.db.repo.RefreshTokenRepository;
import edu.stankin.cogoalauth.exception.AuthenticationFailedException;
import edu.stankin.cogoalauth.service.RefreshTokenService.IssuedRefreshToken;
import edu.stankin.cogoalauth.service.RefreshTokenService.Rotation;
import edu.stankin.cogoalauth.support.TestData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository repository;

    private RefreshTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new RefreshTokenService(repository, TestData.properties(),
                Clock.fixed(TestData.NOW, ZoneOffset.UTC), new SecureRandom());
        user = TestData.user();
    }

    private RefreshToken stored(String value) {
        RefreshToken token = new RefreshToken();
        token.setId(TestData.nextId());
        token.setUser(user);
        token.setFamilyId(UUID.randomUUID());
        token.setTokenHash(RefreshTokenService.hash(value));
        token.setCreatedAt(TestData.NOW.minus(Duration.ofDays(1)));
        token.setExpiresAt(TestData.NOW.plus(Duration.ofDays(29)));
        return token;
    }

    @Test
    void storesOnlyTheHashOfARandomValue() {
        IssuedRefreshToken issued = service.startFamily(user);

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(issued.value()).hasSizeGreaterThanOrEqualTo(43);
        assertThat(saved.getValue().getTokenHash())
                .isEqualTo(RefreshTokenService.hash(issued.value()))
                .isNotEqualTo(issued.value())
                .hasSize(64);
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(TestData.NOW.plus(Duration.ofDays(30)));
        assertThat(issued.expiresIn()).isEqualTo(Duration.ofDays(30).toSeconds());
        assertThat(service.startFamily(user).value()).isNotEqualTo(issued.value());
    }

    @Test
    void rotationRevokesTheOldTokenAndKeepsTheFamily() {
        RefreshToken old = stored("old-value");
        given(repository.findByTokenHashForUpdate(RefreshTokenService.hash("old-value"))).willReturn(Optional.of(old));

        Rotation rotation = service.rotate("old-value");

        assertThat(old.getRevokedAt()).isEqualTo(TestData.NOW);
        assertThat(rotation.user()).isSameAs(user);
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getFamilyId()).isEqualTo(old.getFamilyId());
        assertThat(saved.getValue().getTokenHash()).isEqualTo(RefreshTokenService.hash(rotation.refreshToken().value()));
    }

    @Test
    void reuseOfARevokedTokenRevokesTheWholeFamily() {
        RefreshToken used = stored("used-value");
        used.setRevokedAt(TestData.NOW.minusSeconds(10));
        given(repository.findByTokenHashForUpdate(RefreshTokenService.hash("used-value"))).willReturn(Optional.of(used));

        assertThatThrownBy(() -> service.rotate("used-value"))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessage(AuthenticationFailedException.INVALID_REFRESH_TOKEN);

        verify(repository).revokeFamily(used.getFamilyId(), TestData.NOW);
        verify(repository, never()).save(any());
    }

    @Test
    void expiredTokenIsRejected() {
        RefreshToken expired = stored("expired-value");
        expired.setExpiresAt(TestData.NOW);
        given(repository.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(expired));

        assertThatThrownBy(() -> service.rotate("expired-value")).isInstanceOf(AuthenticationFailedException.class);
        verify(repository, never()).save(any());
        verify(repository, never()).revokeFamily(any(), any());
    }

    @Test
    void unknownTokenIsRejected() {
        given(repository.findByTokenHashForUpdate(anyString())).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.rotate("nope")).isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    void logoutIsIdempotent() {
        RefreshToken token = stored("value");
        token.setRevokedAt(TestData.NOW.minusSeconds(100));
        given(repository.findByTokenHashForUpdate(anyString())).willReturn(Optional.of(token), Optional.empty());

        service.revoke("value");
        service.revoke("unknown");

        // An already revoked token keeps its original revocation time
        assertThat(token.getRevokedAt()).isEqualTo(TestData.NOW.minusSeconds(100));
    }

    @Test
    void logoutRevokesActiveToken() {
        RefreshToken token = stored("value");
        given(repository.findByTokenHashForUpdate(RefreshTokenService.hash("value"))).willReturn(Optional.of(token));

        service.revoke("value");

        assertThat(token.getRevokedAt()).isEqualTo(TestData.NOW);
    }
}
