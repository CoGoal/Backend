package edu.stankin.cogoalgateway;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.regex.Pattern;

/**
 * Rejects paths with dot segments or encoded dots/slashes with 400. Routes match the path as sent, but the gateway
 * forwards it unchanged and Tomcat in the service resolves it, so {@code /main/v3/api-docs/../../internal/users}
 * would otherwise pass the {@code /main/v3/api-docs/**} route and land on main's {@code /internal/users}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class NormalizedPathFilter implements WebFilter {

    private static final Pattern NOT_NORMALIZED = Pattern.compile(
            "(^|/)\\.{1,2}(/|;|$)|%2e|%2f|%5c|\\\\|;", Pattern.CASE_INSENSITIVE);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (rawPath != null && NOT_NORMALIZED.matcher(rawPath).find()) {
            exchange.getResponse().setStatusCode(HttpStatus.BAD_REQUEST);
            return exchange.getResponse().setComplete();
        }
        return chain.filter(exchange);
    }
}
