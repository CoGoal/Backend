package edu.stankin.cogoalmain.config.security;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

@Configuration @EnableConfigurationProperties(JwtProperties.class)
public class SecurityConfig {
    @Bean SecurityFilterChain chain(HttpSecurity http, JwtDecoder decoder) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(a -> a.requestMatchers("/actuator/health", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/error", "/api/v1/HelloWorld").permitAll()
                .requestMatchers("/internal/**").denyAll().anyRequest().authenticated())
            .exceptionHandling(e -> e.authenticationEntryPoint((q,r,x)->error(r,401)).accessDeniedHandler((q,r,x)->error(r,403)))
            .addFilterBefore(new Tokens(decoder), UsernamePasswordAuthenticationFilter.class).build();
    }
    private static void error(HttpServletResponse response, int status) throws IOException {
        response.setStatus(status); response.setContentType("application/json"); response.setCharacterEncoding("UTF-8");
        response.getWriter().write(status==401 ? "{\"code\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\",\"details\":[]}"
            : "{\"code\":\"FORBIDDEN\",\"message\":\"Недостаточно прав\",\"details\":[]}");
    }
    private static class Tokens extends OncePerRequestFilter {
        private final JwtDecoder decoder;
        Tokens(JwtDecoder decoder) { this.decoder=decoder; }
        @Override protected void doFilterInternal(HttpServletRequest q, HttpServletResponse r, FilterChain chain) throws ServletException, IOException {
                String header=q.getHeader("Authorization");
                if (header!=null && header.regionMatches(true,0,"Bearer ",0,7)) {
                    try {
                        var jwt=decoder.decode(header.substring(7).trim());
                        Long id=Long.valueOf(jwt.getSubject());
                        if (id <= 0) throw new IllegalArgumentException("User id must be positive");
                        List<String> roles=jwt.getClaimAsStringList("roles");
                        var authorities=roles.stream().map(role->new SimpleGrantedAuthority("ROLE_"+role)).toList();
                        SecurityContextHolder.getContext().setAuthentication(UsernamePasswordAuthenticationToken.authenticated(id,null,authorities));
                    } catch (RuntimeException e) { SecurityContextHolder.clearContext();error(r,401);return; }
                }
            chain.doFilter(q,r);
        }
    }
}
