package uk.ac.dundee.ga.mms.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import uk.ac.dundee.ga.mms.util.EncryptedStringConverter;

import java.time.Instant;
import java.time.LocalDate;

/** One mentor meeting record with its structured notes (FR-11). Notes columns are AES-GCM encrypted at rest. */
@Entity
@Table(name = "mentor_meeting")
@Getter @Setter @NoArgsConstructor
public class MentorMeeting {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private Long studentId;
    private Long aosUserId;
    private Long mentorId;
    private Long academicYearId;
    private Long periodId;
    private LocalDate meetingDate;
    @Enumerated(EnumType.STRING)
    private MeetingFormat format;
    private boolean studentPresent;
    private boolean mentorPresent;
    @Convert(converter = EncryptedStringConverter.class)
    private String workActivities;
    @Convert(converter = EncryptedStringConverter.class)
    private String universityProgress;
    @Convert(converter = EncryptedStringConverter.class)
    private String universityFeedback;
    @Convert(converter = EncryptedStringConverter.class)
    private String trainingOpportunities;
    @Convert(converter = EncryptedStringConverter.class)
    private String agreedActions;
    private boolean concernFlag;
    @Convert(converter = EncryptedStringConverter.class)
    private String concernNote;
    private LocalDate nextMeetingDate;
    @Enumerated(EnumType.STRING)
    private MeetingStatus status = MeetingStatus.DRAFT;
    private Instant submittedAt;
    @Version
    private Integer version;
    private Long createdBy;
    private Instant createdAt;
    private Long updatedBy;
    private Instant updatedAt;
    private Instant deletedAt;
    private Long deletedBy;
    private String deleteReason;
    /** Idempotency key supplied by the client (also used by V2 for retries). */
    private String clientRequestId;

    public boolean isDeleted() {
        return deletedAt != null;
    }

    public boolean isSubmitted() {
        return status == MeetingStatus.SUBMITTED;
    }
}
