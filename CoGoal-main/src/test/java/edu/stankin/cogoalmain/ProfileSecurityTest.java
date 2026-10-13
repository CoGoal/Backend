package edu.stankin.cogoalmain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.test.web.servlet.MockMvc;
import edu.stankin.cogoalmain.db.repo.UserProfileRepository;
import edu.stankin.cogoalmain.service.ProfileService;
import edu.stankin.cogoalmain.pojo.dto.ProfileSnapshot;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class ProfileSecurityTest {
    private static final AtomicLong IDS = new AtomicLong(1000);
    @Autowired MockMvc mvc;
    @Autowired JwtEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @Autowired ProfileService profiles;
    @Autowired UserProfileRepository repository;
    private String bearer(String subject) {
        Instant now=Instant.now();
        var claims=JwtClaimsSet.builder().issuer("pact-auth").audience(List.of("pact-main"))
                .subject(subject).issuedAt(now).expiresAt(now.plusSeconds(900)).claim("roles",List.of("USER")).build();
        return "Bearer "+encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(),claims)).getTokenValue();
    }
    @Test void profileRequiresValidJwtAndHttpSynchronizationIsDisabled() throws Exception {
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        mvc.perform(get("/api/v1/users/me").header("Authorization","Bearer invalid")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/users").servletPath("/internal/users").header("Authorization",bearer("123")).contentType("application/json").content("{}"))
                .andExpect(status().isForbidden());
        for (String invalid : List.of(UUID.randomUUID().toString(),"0","-1")) {
            mvc.perform(get("/api/v1/users/me").header("Authorization",bearer(invalid))).andExpect(status().isUnauthorized());
        }
    }
    @Test void olderSnapshotCannotOverwriteProfileOrAwardCoinsAgain() throws Exception {
        Long id=IDS.incrementAndGet();
        profiles.sync(snapshot(id,3,"Новое имя",null));
        var stored=repository.findById(id).orElseThrow(); stored.setCoins(47); repository.saveAndFlush(stored);
        profiles.sync(snapshot(id,1,"Старое имя",null));
        mvc.perform(get("/api/v1/users/me").header("Authorization",bearer(id.toString()))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id)).andExpect(jsonPath("$.name").value("Новое имя")).andExpect(jsonPath("$.coins").value(47));
        profiles.sync(snapshot(id,4,"Следующее имя",null));
        assertThat(repository.findById(id).orElseThrow().getCoins()).isEqualTo(47);
    }
    private ProfileSnapshot snapshot(Long id,long version,String name,UUID legacy) {
        return new ProfileSnapshot(id,"user"+id+"@example.com","user"+id,name,null,"USER",version,legacy);
    }
    @Test void concurrentFirstDeliveryCreatesOnlyOneProfile() throws Exception {
        Long id=IDS.incrementAndGet(); ExecutorService executor=Executors.newFixedThreadPool(2);
        try {
            var results=executor.invokeAll(List.<Callable<Boolean>>of(
                ()->profiles.sync(snapshot(id,1,"Имя",null)),()->profiles.sync(snapshot(id,1,"Имя",null))));
            assertThat(List.of(results.get(0).get(),results.get(1).get())).containsExactlyInAnyOrder(true,false);
            assertThat(repository.findById(id).orElseThrow().getCoins()).isEqualTo(100);
        } finally {executor.shutdownNow();}
    }
    @Test void migrationPreservesLegacyBalanceAndCreationDate() {
        Long id=IDS.incrementAndGet();UUID legacy=UUID.randomUUID();Instant created=Instant.parse("2025-01-02T03:04:05Z");
        jdbc.update("INSERT INTO legacy_user_profiles(id,email,username,username_key,name,role,coins,created_at,profile_version) VALUES(?,?,?,?,?,'USER',47,TIMESTAMP '2025-01-02 03:04:05',7)",
            legacy,"legacy"+id+"@example.com","legacy"+id,"legacy"+id,"Старое имя");
        profiles.sync(snapshot(id,7,"Новое имя",legacy));var migrated=repository.findById(id).orElseThrow();
        assertThat(migrated.getCoins()).isEqualTo(47);assertThat(migrated.getCreatedAt()).isEqualTo(created);
        assertThat(migrated.getLegacyId()).isEqualTo(legacy);
    }
    @Test void mainOwnsProfileEditsAndAccountEventsCannotOverwriteThem() throws Exception {
        Long id=IDS.incrementAndGet(); profiles.sync(snapshot(id,1,"Начальное имя",null));
        var created=repository.findById(id).orElseThrow().getCreatedAt();
        mvc.perform(patch("/api/v1/users/me").contentType("application/json").content("{\"name\":\"Имя\"}"))
            .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/v1/users/me").header("Authorization",bearer(id.toString())).contentType("application/json")
            .content("{\"name\":\"Новое имя\",\"avatarUrl\":\"/files/avatar.png\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Новое имя"));
        profiles.sync(new ProfileSnapshot(id,"changed"+id+"@example.com","changed"+id,"Старое имя из очереди","old-avatar","ADMIN",3,null));
        var updated=repository.findById(id).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Новое имя"); assertThat(updated.getAvatarUrl()).isEqualTo("/files/avatar.png");
        assertThat(updated.getUsername()).isEqualTo("changed"+id); assertThat(updated.getRole()).isEqualTo("ADMIN");
        assertThat(updated.getCoins()).isEqualTo(100); assertThat(updated.getCreatedAt()).isEqualTo(created);
        mvc.perform(patch("/api/v1/users/me").header("Authorization",bearer(id.toString())).contentType("application/json")
            .content("{\"name\":\"Другое имя\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").value("/files/avatar.png"));
        mvc.perform(patch("/api/v1/users/me").header("Authorization",bearer(id.toString())).contentType("application/json")
            .content("{\"avatarUrl\":null}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.avatarUrl").isEmpty());
    }
    @Test void profilePatchRejectsLoginAndInvalidDisplayNames() throws Exception {
        Long id=IDS.incrementAndGet(); profiles.sync(snapshot(id,1,"Имя",null));
        for (String body:List.of("{\"username\":\"other_login\"}","{\"name\":\"   \"}","{\"name\":\"\"}")) {
            mvc.perform(patch("/api/v1/users/me").header("Authorization",bearer(id.toString())).contentType("application/json").content(body))
                .andExpect(status().isBadRequest());
        }
        assertThat(repository.findById(id).orElseThrow().getUsername()).isEqualTo("user"+id);
    }
    @Test void lateRegistrationInitializesDisplayFieldsWithoutRevertingLoginAndMainEditsWin() {
        Long id=IDS.incrementAndGet();
        profiles.sync(new ProfileSnapshot(id,"next"+id+"@example.com","next"+id,null,null,"USER",2,null));
        profiles.sync(snapshot(id,1,"Имя при регистрации",null));
        var profile=repository.findById(id).orElseThrow();
        assertThat(profile.getName()).isEqualTo("Имя при регистрации"); assertThat(profile.getUsername()).isEqualTo("next"+id);
        assertThat(profile.getProfileVersion()).isEqualTo(2);
        Long editedId=IDS.incrementAndGet();
        profiles.sync(new ProfileSnapshot(editedId,"next"+editedId+"@example.com","next"+editedId,null,null,"USER",2,null));
        profiles.update(editedId,"Самостоятельное имя","/files/avatar.png",true);
        profiles.sync(snapshot(editedId,1,"Имя при регистрации",null));
        var edited=repository.findById(editedId).orElseThrow();
        assertThat(edited.getName()).isEqualTo("Самостоятельное имя"); assertThat(edited.getAvatarUrl()).isEqualTo("/files/avatar.png");
    }
}
