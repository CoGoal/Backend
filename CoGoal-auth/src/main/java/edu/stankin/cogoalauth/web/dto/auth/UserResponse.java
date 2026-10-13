package edu.stankin.cogoalauth.web.dto.auth;
import java.time.Instant;
import java.util.List;

/** Account data owned by auth. Display name and avatar belong to main. */
public record UserResponse(Long id, String email, String username, List<String> roles, Instant createdAt) {
    @com.fasterxml.jackson.annotation.JsonProperty("role")
    public String role() { return roles.contains("ADMIN") ? "ADMIN" : "USER"; }
}
