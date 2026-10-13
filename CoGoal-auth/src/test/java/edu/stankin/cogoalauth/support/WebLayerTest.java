package edu.stankin.cogoalauth.support;

import edu.stankin.cogoalauth.config.AppConfig;
import edu.stankin.cogoalauth.config.security.JwtConfig;
import edu.stankin.cogoalauth.config.security.RestAccessDeniedHandler;
import edu.stankin.cogoalauth.config.security.RestAuthenticationEntryPoint;
import edu.stankin.cogoalauth.config.security.SecurityConfig;
import edu.stankin.cogoalauth.service.AccessTokenService;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.AliasFor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * {@code @WebMvcTest} slice with the real security setup: JWT validation, path rules and error handlers.
 * Requests carry tokens from {@link TestTokens}; services are {@code @MockitoBean}s.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@WebMvcTest(properties = {
        "security.jwt.secret=" + TestTokens.SECRET,
        "app.internal.token=" + TestTokens.INTERNAL_TOKEN,
        "app.scheduling.enabled=false"
})
@Import({
        SecurityConfig.class, JwtConfig.class, AccessTokenService.class,
        RestAuthenticationEntryPoint.class, RestAccessDeniedHandler.class, AppConfig.class
})
public @interface WebLayerTest {

    /** Controllers under test. */
    @AliasFor(annotation = WebMvcTest.class, attribute = "controllers")
    Class<?>[] value() default {};
}
