package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** One-time token emailed to a new local-account user so they can set a password (from MentorSync invites). */
@Entity
@Table(name = "user_invite")
@Getter @Setter @NoArgsConstructor
public class UserInvite {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long userId;
    private String email;
    private String tokenHash;
    private Instant expiresAt;
    private Instant usedAt;
    private Long invitedBy;
    private Instant createdAt;
}
