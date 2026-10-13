package edu.stankin.cogoalmain.service;
import edu.stankin.cogoalmain.db.entity.UserProfile;
import edu.stankin.cogoalmain.db.repo.*;
import edu.stankin.cogoalmain.pojo.dto.ProfileSnapshot;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Optional;

@Service
public class ProfileService {
    private final UserProfileRepository profiles;
    private final LegacyUserProfileRepository legacyProfiles;
    private final ProfileSyncLockRepository locks;
    private final int initialCoins;
    public ProfileService(UserProfileRepository profiles, LegacyUserProfileRepository legacyProfiles,
                          ProfileSyncLockRepository locks, @Value("${app.profile.initial-coins}") int initialCoins) {
        this.profiles=profiles; this.legacyProfiles=legacyProfiles; this.locks=locks; this.initialCoins=initialCoins;
    }
    @Transactional
    public boolean sync(ProfileSnapshot snapshot) {
        lock(snapshot.id());
        Optional<UserProfile> existing=profiles.findById(snapshot.id());
        UserProfile profile=existing.orElseGet(()->{
            UserProfile created=new UserProfile();
            created.setId(snapshot.id()); created.setLegacyId(snapshot.legacyId());
            created.setCoins(initialCoins); created.setCreatedAt(Instant.now());
            created.setName(snapshot.username());
            if (snapshot.legacyId()!=null) legacyProfiles.findById(snapshot.legacyId()).ifPresent(legacy->{
                created.setCoins(legacy.getCoins()); created.setCreatedAt(legacy.getCreatedAt());
            });
            return created;
        });
        // Initial profile data is independent of the account version: registration may arrive late.
        // Explicit main edits also initialize the profile, so no later event can overwrite them.
        if (!profile.isProfileInitialized() && snapshot.name()!=null) {
            profile.setName(snapshot.name()); profile.setAvatarUrl(snapshot.avatarUrl());
            profile.setProfileInitialized(true);
        }
        if (profile.getProfileVersion()<snapshot.version()) {
            profile.setEmail(snapshot.email()); profile.setUsername(snapshot.username());
            profile.setRole(snapshot.role()); profile.setProfileVersion(snapshot.version());
        }
        profiles.saveAndFlush(profile);
        return existing.isEmpty();
    }
    @Transactional
    public Optional<UserProfile> update(Long id, String name, String avatarUrl, boolean avatarPresent) {
        lock(id);
        return profiles.findById(id).map(profile->{
            if (name!=null) profile.setName(name.trim());
            if (avatarPresent) profile.setAvatarUrl(avatarUrl);
            if (name!=null || avatarPresent) profile.setProfileInitialized(true);
            return profiles.saveAndFlush(profile);
        });
    }
    private void lock(Long id) { locks.lock((int)Math.floorMod(id,64)); }
    @Transactional(readOnly=true)
    public Optional<UserProfile> find(Long id) { return profiles.findById(id); }
}
