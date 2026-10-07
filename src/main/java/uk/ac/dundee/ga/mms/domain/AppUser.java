package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/** A university staff member (or, in Phase 2, a student) who can sign in. */
@Entity
@Table(name = "app_user")
@Getter @Setter @NoArgsConstructor
public class AppUser {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String email;
    private String displayName;
    private String entraOid;
    private String passwordHash;
    /** TOTP secret, AES-GCM encrypted. */
    private String mfaSecret;
    private boolean mfaEnabled;
    private boolean active = true;
    private int failedLogins;
    private Instant lockedUntil;
    private Instant lastLoginAt;
    private Instant createdAt = Instant.now();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"))
    @Column(name = "role")
    @Enumerated(EnumType.STRING)
    private Set<Role> roles = EnumSet.noneOf(Role.class);

    public boolean hasRole(Role r) {
        return roles != null && roles.contains(r);
    }
}
