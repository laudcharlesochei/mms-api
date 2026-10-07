package uk.ac.dundee.ga.mms.service;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.auth.TokenService;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.UserInvite;
import uk.ac.dundee.ga.mms.storage.InviteStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.util.Hashing;
import uk.ac.dundee.ga.mms.util.NotesCipher;
import uk.ac.dundee.ga.mms.util.Totp;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.InviteInfo;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.LoginResponse;
import uk.ac.dundee.ga.mms.web.dto.AuthDtos.MfaSetupResponse;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;

/**
 * Local accounts (FR-01 fallback when Entra SSO is not approved): email + password (BCrypt 12)
 * + TOTP MFA, lockout after repeated failures, short-lived tokens with sliding refresh (FR-04).
 */
@Service
public class AuthService {

    private static final String GENERIC_FAIL = "Email or password is incorrect.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserStore users;
    private final InviteStore invites;
    private final PasswordEncoder encoder;
    private final TokenService tokens;
    private final AuditService audit;
    private final MailService mail;
    private final MmsProperties props;

    public AuthService(UserStore users, InviteStore invites, PasswordEncoder encoder, TokenService tokens,
                       AuditService audit, MailService mail, MmsProperties props) {
        this.users = users;
        this.invites = invites;
        this.encoder = encoder;
        this.tokens = tokens;
        this.audit = audit;
        this.mail = mail;
        this.props = props;
    }

    private void requireLocal() {
        if (props.isEntra()) {
            throw ApiException.badRequest("Local sign-in is disabled; use your university account.");
        }
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse login(String email, String password) {
        requireLocal();
        AppUser u = users.findByEmail(email).orElse(null);
        if (u == null || !u.isActive() || u.getPasswordHash() == null) {
            encoder.matches(password, dummyHash()); // keep timing similar for unknown users
            audit.record(null, email, "SIGN_IN_FAILED", "AppUser", null, Map.of("reason", "unknown or inactive"));
            throw unauthorized(GENERIC_FAIL);
        }
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now())) {
            audit.record(u.getId(), u.getEmail(), "SIGN_IN_FAILED", "AppUser", u.getId(), Map.of("reason", "locked"));
            throw new ApiException(HttpStatus.LOCKED, "account-locked",
                    "Too many failed attempts. Try again after " + props.getAuth().getLockoutMinutes() + " minutes.");
        }
        if (!encoder.matches(password, u.getPasswordHash())) {
            registerFailure(u);
            throw unauthorized(GENERIC_FAIL);
        }
        if (u.isMfaEnabled()) {
            return new LoginResponse(true, tokens.issueMfaChallenge(u), null, null, null, null, false);
        }
        return success(u);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public LoginResponse loginMfa(String challengeToken, String code) {
        requireLocal();
        Jwt jwt;
        try {
            jwt = tokens.decode(challengeToken, "mfa");
        } catch (JwtException e) {
            throw unauthorized("Your sign-in session expired. Start again.");
        }
        AppUser u = users.findById(Long.valueOf(jwt.getSubject())).filter(AppUser::isActive)
                .orElseThrow(() -> unauthorized(GENERIC_FAIL));
        if (u.getLockedUntil() != null && u.getLockedUntil().isAfter(Instant.now())) {
            throw new ApiException(HttpStatus.LOCKED, "account-locked", "Account temporarily locked.");
        }
        if (!Totp.verify(NotesCipher.decrypt(u.getMfaSecret()), code)) {
            registerFailure(u);
            throw unauthorized("The authentication code is incorrect.");
        }
        return success(u);
    }

    public LoginResponse refresh(String refreshToken) {
        requireLocal();
        Jwt jwt;
        try {
            jwt = tokens.decode(refreshToken, "refresh");
        } catch (JwtException e) {
            throw unauthorized("Session expired. Sign in again.");
        }
        AppUser u = users.findById(Long.valueOf(jwt.getSubject())).filter(AppUser::isActive)
                .orElseThrow(() -> unauthorized("Session expired. Sign in again."));
        if (!TokenService.passwordFingerprint(u).equals(jwt.getClaimAsString("pwd"))) {
            throw unauthorized("Session expired. Sign in again.");
        }
        TokenService.TokenPair p = tokens.issue(u);
        return new LoginResponse(false, null, p.accessToken(), p.refreshToken(), p.accessExpiresAt(), p.refreshExpiresAt(),
                props.getAuth().isRequireMfa() && !u.isMfaEnabled());
    }

    private LoginResponse success(AppUser u) {
        u.setFailedLogins(0);
        u.setLockedUntil(null);
        u.setLastLoginAt(Instant.now());
        users.save(u);
        audit.record(u.getId(), u.getEmail(), "SIGN_IN", "AppUser", u.getId(), Map.of("method", "local"));
        TokenService.TokenPair p = tokens.issue(u);
        return new LoginResponse(false, null, p.accessToken(), p.refreshToken(), p.accessExpiresAt(), p.refreshExpiresAt(),
                props.getAuth().isRequireMfa() && !u.isMfaEnabled());
    }

    private void registerFailure(AppUser u) {
        int n = u.getFailedLogins() + 1;
        u.setFailedLogins(n);
        if (n >= props.getAuth().getMaxFailedLogins()) {
            u.setLockedUntil(Instant.now().plus(props.getAuth().getLockoutMinutes(), ChronoUnit.MINUTES));
            u.setFailedLogins(0);
            audit.record(u.getId(), u.getEmail(), "ACCOUNT_LOCKED", "AppUser", u.getId(), Map.of());
        }
        users.save(u);
        audit.record(u.getId(), u.getEmail(), "SIGN_IN_FAILED", "AppUser", u.getId(), Map.of("reason", "bad credentials"));
    }

    /** Entra mode: the SPA calls this once after MSAL sign-in so the event is audited (FR-25). */
    @Transactional
    public void recordSession(CurrentUser cu) {
        users.findById(cu.id()).ifPresent(u -> {
            u.setLastLoginAt(Instant.now());
            users.save(u);
        });
        audit.record(cu.id(), cu.email(), "SIGN_IN", "AppUser", cu.id(), Map.of("method", props.isEntra() ? "entra" : "local"));
    }

    public void signOut(CurrentUser cu) {
        audit.record(cu.id(), cu.email(), "SIGN_OUT", "AppUser", cu.id(), Map.of());
    }

    // ------------------------------------------------------------------ MFA

    @Transactional
    public MfaSetupResponse mfaSetup(CurrentUser cu) {
        requireLocal();
        AppUser u = users.findById(cu.id()).orElseThrow();
        String secret = Totp.newSecret();
        u.setMfaSecret(NotesCipher.encrypt(secret));
        u.setMfaEnabled(false);
        users.save(u);
        return new MfaSetupResponse(secret, Totp.otpauthUri("UoD GA MMS", u.getEmail(), secret));
    }

    @Transactional
    public void mfaEnable(CurrentUser cu, String code) {
        AppUser u = users.findById(cu.id()).orElseThrow();
        if (u.getMfaSecret() == null || !Totp.verify(NotesCipher.decrypt(u.getMfaSecret()), code)) {
            throw ApiException.validation(Map.of("code", "The code does not match. Check your authenticator app's clock."));
        }
        u.setMfaEnabled(true);
        users.save(u);
        audit.record("MFA_ENABLED", "AppUser", u.getId(), Map.of());
    }

    @Transactional
    public void mfaDisable(CurrentUser cu, String code) {
        AppUser u = users.findById(cu.id()).orElseThrow();
        if (!u.isMfaEnabled() || !Totp.verify(NotesCipher.decrypt(u.getMfaSecret()), code)) {
            throw ApiException.validation(Map.of("code", "The code does not match."));
        }
        if (props.getAuth().isRequireMfa()) {
            throw ApiException.forbidden("MFA is required for all accounts.");
        }
        u.setMfaEnabled(false);
        u.setMfaSecret(null);
        users.save(u);
        audit.record("MFA_DISABLED", "AppUser", u.getId(), Map.of());
    }

    @Transactional
    public void changePassword(CurrentUser cu, String current, String next) {
        requireLocal();
        AppUser u = users.findById(cu.id()).orElseThrow();
        if (u.getPasswordHash() == null || !encoder.matches(current, u.getPasswordHash())) {
            throw ApiException.validation(Map.of("currentPassword", "Current password is incorrect"));
        }
        checkPasswordPolicy(next);
        u.setPasswordHash(encoder.encode(next));
        users.save(u);
        audit.record("PASSWORD_CHANGED", "AppUser", u.getId(), Map.of());
    }

    public static void checkPasswordPolicy(String p) {
        if (p == null || p.length() < 10 || !p.matches(".*[A-Za-z].*") || !p.matches(".*\\d.*")) {
            throw ApiException.validation(Map.of("password", "Use at least 10 characters including letters and numbers"));
        }
    }

    // ------------------------------------------------------------------ invites (from MentorSync)

    @Transactional
    public String createInvite(AppUser u, Long invitedBy) {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UserInvite inv = new UserInvite();
        inv.setUserId(u.getId());
        inv.setEmail(u.getEmail());
        inv.setTokenHash(Hashing.sha256(token));
        inv.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        inv.setInvitedBy(invitedBy);
        inv.setCreatedAt(Instant.now());
        invites.save(inv);
        String link = props.getFrontendUrl() + "/invite?token=" + token;
        mail.invite(u.getEmail(), u.getDisplayName(), link);
        audit.record("INVITE_SENT", "AppUser", u.getId(), Map.of());
        return link;
    }

    public InviteInfo validateInvite(String token) {
        UserInvite inv = invites.findByTokenHash(Hashing.sha256(token)).orElse(null);
        if (inv == null || inv.getUsedAt() != null || inv.getExpiresAt().isBefore(Instant.now())) {
            return new InviteInfo(null, null, false);
        }
        AppUser u = users.findById(inv.getUserId()).orElse(null);
        return new InviteInfo(inv.getEmail(), u == null ? null : u.getDisplayName(), u != null && u.isActive());
    }

    @Transactional
    public LoginResponse acceptInvite(String token, String password) {
        requireLocal();
        UserInvite inv = invites.findByTokenHash(Hashing.sha256(token)).orElse(null);
        if (inv == null || inv.getUsedAt() != null || inv.getExpiresAt().isBefore(Instant.now())) {
            throw ApiException.badRequest("This invitation link is invalid or has expired. Ask an Administrator for a new one.");
        }
        AppUser u = users.findById(inv.getUserId()).filter(AppUser::isActive)
                .orElseThrow(() -> ApiException.badRequest("This account is not active."));
        checkPasswordPolicy(password);
        u.setPasswordHash(encoder.encode(password));
        users.save(u);
        inv.setUsedAt(Instant.now());
        invites.save(inv);
        audit.record(u.getId(), u.getEmail(), "INVITE_ACCEPTED", "AppUser", u.getId(), Map.of());
        return success(u);
    }

    private volatile String dummyHash;

    private String dummyHash() {
        if (dummyHash == null) {
            dummyHash = encoder.encode("not-a-real-password-1");
        }
        return dummyHash;
    }

    private static ApiException unauthorized(String msg) {
        return new ApiException(HttpStatus.UNAUTHORIZED, "invalid-credentials", msg);
    }
}
