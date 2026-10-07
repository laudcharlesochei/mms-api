package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.service.AuthService;
import uk.ac.dundee.ga.mms.service.MessagingService;
import uk.ac.dundee.ga.mms.service.ScopeService;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.ChangePasswordRequest;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.CodeRequest;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.InviteAcceptRequest;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.InviteInfo;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.InviteTokenRequest;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.LoginRequest;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.LoginResponse;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.MeResponse;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.MfaLoginRequest;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.MfaSetupResponse;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.PublicConfig;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.RefreshRequest;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Auth", description = "Sign-in, MFA, current user and public configuration")
public class AuthController {

    private final AuthService auth;
    private final CurrentUserService current;
    private final ScopeService scope;
    private final StudentStore students;
    private final MessagingService messaging;
    private final MmsProperties props;

    public AuthController(AuthService auth, CurrentUserService current, ScopeService scope, StudentStore students,
                          MessagingService messaging, MmsProperties props) {
        this.auth = auth;
        this.current = current;
        this.scope = scope;
        this.students = students;
        this.messaging = messaging;
        this.props = props;
    }

    @GetMapping("/config")
    public PublicConfig config() {
        MmsProperties.Entra e = props.getAuth().getEntra();
        return new PublicConfig(props.isEntra() ? "entra" : "local", e.getTenantId(), e.getClientId(), e.getApiScope(),
                props.getFeatures().isMessaging(), props.getEditWindowDays(), MeetingDtos.NOTES_MAX,
                props.getAuth().getRefreshTokenMinutes(), "V1", "jawsdb-mysql");
    }

    @PostMapping("/auth/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest r) {
        return auth.login(r.email(), r.password());
    }

    @PostMapping("/auth/login/mfa")
    public LoginResponse loginMfa(@Valid @RequestBody MfaLoginRequest r) {
        return auth.loginMfa(r.challengeToken(), r.code());
    }

    @PostMapping("/auth/refresh")
    public LoginResponse refresh(@Valid @RequestBody RefreshRequest r) {
        return auth.refresh(r.refreshToken());
    }

    @PostMapping("/auth/session")
    public ResponseEntity<Void> session() {
        auth.recordSession(current.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout() {
        auth.signOut(current.get());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/mfa/setup")
    public MfaSetupResponse mfaSetup() {
        return auth.mfaSetup(current.get());
    }

    @PostMapping("/auth/mfa/enable")
    public ResponseEntity<Void> mfaEnable(@Valid @RequestBody CodeRequest r) {
        auth.mfaEnable(current.get(), r.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/mfa/disable")
    public ResponseEntity<Void> mfaDisable(@Valid @RequestBody CodeRequest r) {
        auth.mfaDisable(current.get(), r.code());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest r) {
        auth.changePassword(current.get(), r.currentPassword(), r.newPassword());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/auth/invites/validate")
    public InviteInfo validateInvite(@Valid @RequestBody InviteTokenRequest r) {
        return auth.validateInvite(r.token());
    }

    @PostMapping("/auth/invites/accept")
    public LoginResponse acceptInvite(@Valid @RequestBody InviteAcceptRequest r) {
        return auth.acceptInvite(r.token(), r.password());
    }

    @GetMapping("/me")
    public MeResponse me() {
        CurrentUser u = current.get();
        Long studentId = u.has(Role.STUDENT) ? students.findByUniEmail(u.email()).map(s -> s.getId()).orElse(null) : null;
        List<String> scopes = new ArrayList<>();
        if (u.seesAll()) {
            scopes.add("all");
        }
        if (u.has(Role.PROG_LEAD)) {
            scopes.add("programme");
        }
        if (u.canAdvise()) {
            scopes.add("advisees");
        }
        if (u.has(Role.STUDENT)) {
            scopes.add("own");
        }
        return new MeResponse(u.id(), u.email(), u.displayName(), u.roles(), current.entity().isMfaEnabled(),
                props.isEntra() ? "entra" : "local", new ArrayList<>(scope.ledProgrammeIds(u)), studentId,
                String.join(",", scopes), messaging.unread(u));
    }
}
