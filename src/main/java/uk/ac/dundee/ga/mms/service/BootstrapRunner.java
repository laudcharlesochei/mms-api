package uk.ac.dundee.ga.mms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.storage.UserStore;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Optional;

/**
 * Makes sure the Administrator named in ADMIN_EMAIL exists, so a fresh JawsDB database can be
 * signed into without hand-written SQL. If that account already exists nothing changes, unless
 * ADMIN_RESET=true, which resets its password to ADMIN_PASSWORD, unlocks and reactivates it
 * (remove ADMIN_RESET afterwards).
 */
@Slf4j
@Component
@Order(1)
public class BootstrapRunner implements ApplicationRunner {

    private final UserStore users;
    private final PasswordEncoder encoder;
    private final MmsProperties props;

    public BootstrapRunner(UserStore users, PasswordEncoder encoder, MmsProperties props) {
        this.users = users;
        this.encoder = encoder;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (props.isMigrateOnly()) {
            return;
        }
        MmsProperties.Bootstrap b = props.getBootstrap();
        String email = b.getAdminEmail() == null ? "" : b.getAdminEmail().trim().toLowerCase(Locale.ROOT);
        String pwd = b.getAdminPassword();
        if (email.isEmpty()) {
            if (users.count() == 0) {
                log.warn("The user table is empty and ADMIN_EMAIL is not set - nobody can sign in. "
                        + "Set ADMIN_EMAIL and ADMIN_PASSWORD, then restart the app.");
            }
            return;
        }
        Optional<AppUser> existing = users.findByEmail(email);
        if (existing.isEmpty()) {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setDisplayName(b.getAdminName());
            u.setRoles(EnumSet.of(Role.ADMIN));
            u.setCreatedAt(Instant.now());
            if (pwd != null && !pwd.isBlank()) {
                u.setPasswordHash(encoder.encode(pwd));
            } else if (!props.isEntra()) {
                log.warn("ADMIN_PASSWORD is not set - the Administrator {} cannot sign in with a password.", email);
            }
            users.save(u);
            log.info("Created bootstrap Administrator {}", email);
            return;
        }
        if (b.isAdminReset()) {
            AppUser u = existing.get();
            if (pwd != null && !pwd.isBlank()) {
                u.setPasswordHash(encoder.encode(pwd));
            }
            u.setActive(true);
            u.setFailedLogins(0);
            u.setLockedUntil(null);
            u.setMfaEnabled(false);
            u.setMfaSecret(null);
            EnumSet<Role> roles = u.getRoles() == null || u.getRoles().isEmpty() ? EnumSet.noneOf(Role.class) : EnumSet.copyOf(u.getRoles());
            roles.add(Role.ADMIN);
            u.setRoles(roles);
            users.save(u);
            log.warn("ADMIN_RESET=true: reset password, MFA and lockout for {}. Remove ADMIN_RESET now: heroku config:unset ADMIN_RESET", email);
        }
    }
}
