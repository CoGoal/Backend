package edu.stankin.cogoalauth.config.security;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
final class SecurityErrorResponses {
    private SecurityErrorResponses() {}
    static void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
        response.setStatus(status.value()); response.setContentType("application/json"); response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(status.value() == 403
            ? "{\"code\":\"FORBIDDEN\",\"message\":\"Недостаточно прав\",\"details\":[]}"
            : "{\"code\":\"UNAUTHORIZED\",\"message\":\"Требуется авторизация\",\"details\":[]}");
    }
}
