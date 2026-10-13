package edu.stankin.cogoalauth.kafka.event;
import java.util.UUID;
@com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
public record UserProfileSnapshot(Long id, String email, String username, String name, String avatarUrl,
                              String role, long version, UUID legacyId) {
    public UserProfileSnapshot(Long id, String email, String username, String name, String avatarUrl, String role, long version) {
        this(id, email, username, name, avatarUrl, role, version, null);
    }
    public UserProfileSnapshot(Long id, String email, String username) { this(id, email, username, username, null, "USER", 1, null); }
}
