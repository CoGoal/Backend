package edu.stankin.cogoalauth.config.security;

import edu.stankin.cogoalauth.config.AppProperties;
import edu.stankin.cogoalauth.service.AccessTokenService;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.time.Duration;
import java.util.List;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {

    public static final String API_PREFIX = "/api/v1/auth";

    private static final String[] PUBLIC_ENDPOINTS = {
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/actuator/health",
            "/error"
    };

    // Authenticated by the credentials or the refresh token in the body, not by an access token
    private static final String[] PUBLIC_AUTH_ENDPOINTS = {
            API_PREFIX + "/register",
            API_PREFIX + "/login",
            API_PREFIX + "/refresh",
            API_PREFIX + "/password/forgot",
            API_PREFIX + "/password/reset",
            API_PREFIX + "/email/confirm"
    };

    private static final String ADMIN_ENDPOINTS = API_PREFIX + "/admin/**";

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   AccessTokenService accessTokenService,
                                                   RestAuthenticationEntryPoint authenticationEntryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(PUBLIC_ENDPOINTS).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_AUTH_ENDPOINTS).permitAll()
                        .requestMatchers(ADMIN_ENDPOINTS).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .addFilterBefore(new JwtAuthenticationFilter(accessTokenService, authenticationEntryPoint),
                        UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    /**
     * Picked up by {@code cors(withDefaults())}. With no origins configured no CORS headers are sent,
     * so browsers on other origins are refused.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        List<String> origins = properties.cors().allowedOrigins().stream()
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList();
        if (origins.isEmpty()) {
            return source;
        }
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE));
        cors.setMaxAge(Duration.ofHours(1));
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    // BCrypt uses at most 72 bytes of a password; longer passwords are rejected by validation
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Users are authenticated by this service's own endpoints and bearer tokens; this stops Spring Boot
    // from creating the default in-memory user with a generated password.
    @Bean
    public UserDetailsService userDetailsService() {
        return new InMemoryUserDetailsManager();
    }
}
