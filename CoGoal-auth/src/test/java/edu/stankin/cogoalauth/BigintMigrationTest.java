package edu.stankin.cogoalauth;

import edu.stankin.cogoalauth.support.DatabaseAvailableCondition;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.core.io.DefaultResourceLoader;
import org.testcontainers.postgresql.PostgreSQLContainer;
import javax.sql.DataSource;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(DatabaseAvailableCondition.class)
class BigintMigrationTest {
    private void migrate(DataSource source,String changelog) throws Exception {
        SpringLiquibase liquibase=new SpringLiquibase();
        liquibase.setDataSource(source);liquibase.setChangeLog(changelog);
        liquibase.setResourceLoader(new DefaultResourceLoader());liquibase.afterPropertiesSet();
    }
    @Test void existingUuidAccountsTokensAndOutboxSurviveAndRerunIsIdempotent() throws Exception {
        try (var postgres=new PostgreSQLContainer("postgres:16-alpine")) {
            postgres.start();
            var source=new DriverManagerDataSource(postgres.getJdbcUrl(),postgres.getUsername(),postgres.getPassword());
            var jdbc=new JdbcTemplate(source);
            migrate(source,"classpath:db/legacy-test-master.xml");
            UUID legacy=UUID.randomUUID();UUID family=UUID.randomUUID();
            jdbc.update("INSERT INTO users(id,email,username,password_hash,name,profile_version,failed_login_attempts,created_at,updated_at) VALUES(?,?,?,?,?,7,2,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                legacy,"migration@example.com","migration_user","preserved-hash","Имя");
            jdbc.update("INSERT INTO user_role(user_id,role) VALUES(?,'ADMIN')",legacy);
            jdbc.update("INSERT INTO refresh_token(id,user_id,family_id,token_hash,created_at,expires_at) VALUES(?,?,?,?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP + interval '1 day')",
                UUID.randomUUID(),legacy,family,"a".repeat(64));
            jdbc.update("INSERT INTO account_action_token(id,user_id,token_hash,purpose,email,expires_at,consumed) VALUES(?,?,?,'EMAIL_CHANGE','new@example.com',CURRENT_TIMESTAMP + interval '30 minute',false)",
                UUID.randomUUID(),legacy,"b".repeat(64));
            migrate(source,"classpath:db/db.changelog-master.xml");
            Long id=jdbc.queryForObject("SELECT id FROM users WHERE legacy_id=?",Long.class,legacy);
            assertThat(id).isPositive();
            assertThat(jdbc.queryForMap("SELECT password_hash,role,profile_version,failed_login_attempts FROM users WHERE id=?",id))
                .containsEntry("password_hash","preserved-hash").containsEntry("role","ADMIN")
                .containsEntry("profile_version",7L).containsEntry("failed_login_attempts",2);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM auth_tokens WHERE user_id=?",Integer.class,id)).isEqualTo(2);
            assertThat(jdbc.queryForMap("SELECT type,new_email,used_at FROM auth_tokens WHERE token_hash=?","b".repeat(64)))
                .containsEntry("type","EMAIL_CONFIRM").containsEntry("new_email","new@example.com").containsEntry("used_at",null);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='legacy_account_action_token'",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name IN ('legacy_users','legacy_user_role','legacy_refresh_token')",Integer.class)).isZero();
            assertThat(jdbc.queryForObject("SELECT family_id FROM auth_tokens WHERE token_hash=?",UUID.class,"a".repeat(64))).isEqualTo(family);
            assertThat(jdbc.queryForMap("SELECT user_id,legacy_user_id,status,profile_version FROM main_profile_outbox"))
                .containsEntry("user_id",id).containsEntry("legacy_user_id",legacy).containsEntry("status","PENDING").containsEntry("profile_version",7L);
            assertThat(jdbc.queryForObject("SELECT initial_name FROM main_profile_outbox",String.class)).isEqualTo("Имя");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' AND table_name='users' AND column_name IN ('name','avatar_url')",Integer.class)).isZero();
            migrate(source,"classpath:db/db.changelog-master.xml");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM users",Integer.class)).isEqualTo(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM main_profile_outbox",Integer.class)).isEqualTo(1);
        }
    }
}
