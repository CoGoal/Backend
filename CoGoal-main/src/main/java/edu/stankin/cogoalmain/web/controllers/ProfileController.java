package edu.stankin.cogoalmain.web.controllers;
import edu.stankin.cogoalmain.service.ProfileService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.*;
import edu.stankin.cogoalmain.db.entity.UserProfile;
import edu.stankin.cogoalmain.pojo.dto.ProfileUpdate;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;

@RestController
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) { this.profiles = profiles; }

    @GetMapping("/api/v1/users/me")
    public ResponseEntity<?> me(@AuthenticationPrincipal Long id) {
        var found = profiles.find(id);
        if (found.isEmpty()) return ResponseEntity.status(404).body(Map.of("code", "PROFILE_PENDING",
            "message", "Профиль ещё создаётся. Повторите запрос позже", "details", List.of()));
        return ResponseEntity.ok(response(found.get()));
    }

    @PatchMapping("/api/v1/users/me") @SecurityRequirement(name="bearerAuth")
    @Operation(summary="Обновить имя или аватар профиля")
    public ResponseEntity<?> update(@AuthenticationPrincipal Long id, @Valid @RequestBody ProfileUpdate request) {
        var found = profiles.update(id,request.name,request.avatarUrl,request.avatarPresent);
        if (found.isEmpty()) return ResponseEntity.status(404).body(Map.of("code","PROFILE_PENDING",
            "message","Профиль ещё создаётся. Повторите запрос позже","details",List.of()));

        return ResponseEntity.ok(response(found.get()));
    }

    private Map<String,Object> response(UserProfile profile) {
        Map<String,Object> result = new LinkedHashMap<>();
        result.put("id", profile.getId());
        result.put("username", profile.getUsername());
        result.put("email", profile.getEmail());
        result.put("name", profile.getName());
        result.put("avatarUrl", profile.getAvatarUrl());
        result.put("activeItem", null);
        result.put("coins", profile.getCoins());
        result.put("role", profile.getRole());
        result.put("createdAt", profile.getCreatedAt());
        result.put("achievements", List.of());
        result.put("stats", Map.of("completedGoals",0,"activePacts",0,"failedGoals",0));
        return result;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict() { return ResponseEntity.status(409).body(Map.of("code","PROFILE_CONFLICT",
        "message","Данные профиля уже используются","details",List.of())); }
}
