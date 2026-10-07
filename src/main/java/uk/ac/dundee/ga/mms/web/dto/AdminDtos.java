package uk.ac.dundee.ga.mms.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uk.ac.dundee.ga.mms.domain.ImportStatus;
import uk.ac.dundee.ga.mms.domain.Role;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Users, imports, audit and messaging bodies. */
public final class AdminDtos {

    private AdminDtos() {
    }

    public record UserDto(Long id, String email, String displayName, Set<Role> roles, boolean active,
                          boolean mfaEnabled, boolean entraLinked, boolean hasPassword, Instant lastLoginAt,
                          Instant createdAt) {
    }

    public record CreateUserRequest(
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 160) String displayName,
            @NotEmpty Set<Role> roles,
            @Size(min = 10, max = 100) String password,
            boolean sendInvite) {
    }

    public record UpdateUserRequest(@Size(max = 160) String displayName, Boolean active, Set<Role> roles) {
    }

    public record RolesRequest(@NotNull Set<Role> roles) {
    }

    public record ImportRowDto(int rowNo, String matricNo, String firstName, String lastName, String studentEmail,
                               String programmeCode, String aosEmail, String employerName, String mentorName,
                               String mentorEmail, String academicYear, String action, List<String> errors) {
    }

    public record ImportPreview(Long batchId, String fileName, ImportStatus status, int rowsTotal, int rowsOk,
                                int rowsError, int rowsAdd, int rowsUpdate, Instant uploadedAt,
                                Instant committedAt, List<ImportRowDto> rows) {
    }

    public record ImportBatchSummary(Long id, String fileName, ImportStatus status, int rowsTotal, int rowsOk,
                                     int rowsError, Instant uploadedAt, String uploadedBy) {
    }

    public record AuditDto(Long id, Instant occurredAt, Long userId, String userEmail, String action,
                           String entityType, String entityId, String details) {
    }

    public record ContactDto(Long id, String displayName, String email, Set<Role> roles, long unread,
                             Instant lastMessageAt) {
    }

    public record MessageDto(Long id, Long senderId, Long recipientId, String body, Instant createdAt, Instant readAt,
                             boolean mine) {
    }

    public record SendMessageRequest(@NotNull Long recipientId, @NotBlank @Size(max = 4000) String body) {
    }
}
