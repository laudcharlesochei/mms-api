package uk.ac.dundee.ga.mms.service;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.util.TextSanitizer;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.CreateUserRequest;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.UpdateUserRequest;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.UserDto;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.time.Instant;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Users and roles (FR-03): create, deactivate, assign roles; invitations for local accounts. */
@Service
public class UserAdminService {

    private final UserStore users;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final AuditService audit;
    private final MmsProperties props;

    public UserAdminService(UserStore users, PasswordEncoder encoder, AuthService auth, AuditService audit, MmsProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.auth = auth;
        this.audit = audit;
        this.props = props;
    }

    public List<UserDto> list(Role role, String q) {
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        return users.findAll().stream()
                .filter(u -> role == null || u.hasRole(role))
                .filter(u -> needle == null || needle.isEmpty() || u.getEmail().toLowerCase(Locale.ROOT).contains(needle)
                        || u.getDisplayName().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(AppUser::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                .map(UserAdminService::toDto).toList();
    }

    /** Lightweight list of active staff with a role, for pickers (e.g. AoS reassignment). */
    public List<UserDto> staff(Role role) {
        return list(role, null).stream().filter(UserDto::active).toList();
    }

    @Transactional
    public UserDto create(CurrentUser by, CreateUserRequest r) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (users.findByEmail(email).isPresent()) {
            throw ApiException.conflict("duplicate", "A user with this email already exists");
        }
        AppUser u = new AppUser();
        u.setEmail(email);
        u.setDisplayName(TextSanitizer.clean(r.displayName()));
        u.setRoles(EnumSet.copyOf(r.roles()));
        u.setActive(true);
        u.setCreatedAt(Instant.now());
        if (r.password() != null && !r.password().isBlank()) {
            AuthService.checkPasswordPolicy(r.password());
            u.setPasswordHash(encoder.encode(r.password()));
        }
        u = users.save(u);
        audit.record("USER_CREATE", "AppUser", u.getId(), Map.of("roles", r.roles().toString()));
        if (!props.isEntra() && r.sendInvite()) {
            auth.createInvite(u, by.id());
        }
        return toDto(u);
    }

    @Transactional
    public UserDto update(CurrentUser by, Long id, UpdateUserRequest r) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        if (r.displayName() != null && !r.displayName().isBlank()) {
            u.setDisplayName(TextSanitizer.clean(r.displayName()));
        }
        if (r.active() != null && r.active() != u.isActive()) {
            if (!r.active() && u.getId().equals(by.id())) {
                throw ApiException.badRequest("You cannot deactivate your own account.");
            }
            u.setActive(r.active());
            audit.record(r.active() ? "USER_ACTIVATE" : "USER_DEACTIVATE", "AppUser", id, Map.of());
        }
        if (r.roles() != null) {
            applyRoles(by, u, r.roles());
        }
        return toDto(users.save(u));
    }

    @Transactional
    public UserDto setRoles(CurrentUser by, Long id, Set<Role> roles) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        applyRoles(by, u, roles);
        return toDto(users.save(u));
    }

    private void applyRoles(CurrentUser by, AppUser u, Set<Role> roles) {
        if (u.getId().equals(by.id()) && u.hasRole(Role.ADMIN) && !roles.contains(Role.ADMIN)) {
            throw ApiException.badRequest("You cannot remove your own Administrator role.");
        }
        u.setRoles(roles.isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(roles));
        audit.record("USER_ROLES", "AppUser", u.getId(), Map.of("roles", roles.toString()));
    }

    @Transactional
    public UserDto resetMfa(Long id) {
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        u.setMfaEnabled(false);
        u.setMfaSecret(null);
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        audit.record("USER_MFA_RESET", "AppUser", id, Map.of());
        return toDto(users.save(u));
    }

    @Transactional
    public Map<String, String> sendInvite(CurrentUser by, Long id) {
        if (props.isEntra()) {
            throw ApiException.badRequest("Invitations are only used with local accounts; Entra users sign in with SSO.");
        }
        AppUser u = users.findById(id).orElseThrow(() -> ApiException.notFound("User"));
        String link = auth.createInvite(u, by.id());
        return props.getMail().isEnabled() ? Map.of("status", "sent") : Map.of("status", "mail-disabled", "link", link);
    }

    public static UserDto toDto(AppUser u) {
        return new UserDto(u.getId(), u.getEmail(), u.getDisplayName(), u.getRoles(), u.isActive(), u.isMfaEnabled(),
                u.getEntraOid() != null, u.getPasswordHash() != null, u.getLastLoginAt(), u.getCreatedAt());
    }
}
