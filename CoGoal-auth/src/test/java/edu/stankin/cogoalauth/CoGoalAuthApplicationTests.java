package edu.stankin.cogoalauth;

import edu.stankin.cogoalauth.support.PostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Starts the full context against a real PostgreSQL: Liquibase applies all changesets
 * and Hibernate validates every entity against the resulting schema ({@code ddl-auto=validate}).
 */
class CoGoalAuthApplicationTests extends PostgresIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndSchemaMatchesEntities() {
        // Context startup itself fails if Liquibase or schema validation fails
    }

    @Test
    void usernameIsUniqueIgnoringCase() {
        String username = "Case" + randomSuffix();
        insertUser("first-" + UUID.randomUUID() + "@example.com", username);

        assertThatThrownBy(() -> insertUser("second-" + UUID.randomUUID() + "@example.com", username.toLowerCase()))
                .hasMessageContaining("ux_users_username_lower");
    }

    @Test
    void emailMustBeLowerCase() {
        assertThatThrownBy(() -> insertUser("Upper-" + UUID.randomUUID() + "@example.com", "u" + randomSuffix()))
                .hasMessageContaining("ck_users_email_lower");
    }

    @Test
    void usersIdIsGeneratedBigint() {
        var metadata=jdbcTemplate.queryForMap("select data_type,is_identity,identity_generation from information_schema.columns where table_name='users' and column_name='id'");
        assertThat(metadata).containsEntry("data_type","bigint").containsEntry("is_identity","YES").containsEntry("identity_generation","ALWAYS");
    }

    // Tests may run against a reused external database, so names must not repeat between runs
    private static String randomSuffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private void insertUser(String email, String username) {
        jdbcTemplate.update("""
                insert into users (email, username, password_hash, created_at, updated_at)
                values (?, ?, 'x', now(), now())""", email, username);
    }
}
