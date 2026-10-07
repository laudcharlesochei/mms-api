package uk.ac.dundee.ga.mms.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import uk.ac.dundee.ga.mms.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(@NotBlank String email, @NotBlank String password) {
    }

    public record MfaLoginRequest(@NotBlank String challengeToken, @NotBlank String code) {
    }

    public record RefreshRequest(@NotBlank String refreshToken) {
    }

    /** Either tokens, or {@code mfaRequired=true} with a challenge token for the TOTP step. */
    public record LoginResponse(boolean mfaRequired, String challengeToken, String accessToken, String refreshToken,
                                Instant accessExpiresAt, Instant refreshExpiresAt, boolean mfaSetupRequired) {
    }

    public record MfaSetupResponse(String secret, String otpauthUri) {
    }

    public record CodeRequest(@NotBlank String code) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank @Size(min = 10, max = 100) String newPassword) {
    }

    public record InviteTokenRequest(@NotBlank String token) {
    }

    public record InviteAcceptRequest(@NotBlank String token, @NotBlank @Size(min = 10, max = 100) String password) {
    }

    public record InviteInfo(String email, String displayName, boolean valid) {
    }

    public record MeResponse(Long id, String email, String displayName, Set<Role> roles, boolean mfaEnabled,
                             String authMode, List<Long> ledProgrammeIds, Long studentId, String scope,
                             long unreadMessages) {
    }

    public record PublicConfig(String authMode, String entraTenantId, String entraClientId, String entraApiScope,
                               boolean messagingEnabled, int editWindowDays, int notesMaxLength,
                               int idleTimeoutMinutes, String variant, String storage) {
    }
}
