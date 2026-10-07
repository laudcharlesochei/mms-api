package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/** Append-only audit trail (FR-25). */
@Entity
@Table(name = "audit_event")
@Getter @Setter @NoArgsConstructor
public class AuditEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Instant occurredAt;
    private Long userId;
    private String userEmail;
    private String action;
    private String entityType;
    private String entityId;
    private String ipHash;
    private String detailsJson;
}
