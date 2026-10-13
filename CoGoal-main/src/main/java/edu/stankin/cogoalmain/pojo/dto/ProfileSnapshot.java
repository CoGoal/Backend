package edu.stankin.cogoalmain.pojo.dto;
import jakarta.validation.constraints.*;
import java.util.UUID;
public record ProfileSnapshot(@NotNull @Positive Long id, @NotBlank @Email String email,
    @NotBlank @Pattern(regexp="^[a-zA-Z0-9_]{3,32}$") String username, @Size(min=1,max=100) @Pattern(regexp=".*\\S.*") String name,
    @Size(max=2048) String avatarUrl, @NotNull @Pattern(regexp="USER|ADMIN") String role,
    @Min(1) long version, UUID legacyId) {}
