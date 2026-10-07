package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.dundee.ga.mms.util.EncryptedStringConverter;

import java.time.Instant;

/** Snapshot of a meeting written on every amendment after submit (FR-15). */
@Entity
@Table(name = "meeting_revision")
@Getter @Setter @NoArgsConstructor
public class MeetingRevision {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long meetingId;
    private int version;
    @Convert(converter = EncryptedStringConverter.class)
    private String snapshotJson;
    private Long changedBy;
    private Instant changedAt;
}
