package uk.ac.dundee.ga.mms.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import uk.ac.dundee.ga.mms.domain.MeetingFormat;
import uk.ac.dundee.ga.mms.domain.MeetingStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Mentor meeting bodies (FR-10 to FR-18). */
public final class MeetingDtos {

    private MeetingDtos() {
    }

    public static final int NOTES_MAX = 2000;
    public static final int CONCERN_MAX = 500;

    public record MeetingRequest(
            @NotNull Long studentId,
            LocalDate meetingDate,
            MeetingFormat format,
            Boolean studentPresent,
            Boolean mentorPresent,
            Long mentorId,
            @Size(max = NOTES_MAX) String workActivities,
            @Size(max = NOTES_MAX) String universityProgress,
            @Size(max = NOTES_MAX) String universityFeedback,
            @Size(max = NOTES_MAX) String trainingOpportunities,
            @Size(max = NOTES_MAX) String agreedActions,
            Boolean concernFlag,
            @Size(max = CONCERN_MAX) String concernNote,
            LocalDate nextMeetingDate,
            MeetingStatus status,
            Boolean confirmDuplicate,
            @Size(max = 64) String clientRequestId) {
    }

    public record MeetingDto(Long id, Long studentId, String studentName, String matricNo, String programmeName,
                             String employerName, Long aosUserId, String aosName, String aosEmail, Long mentorId,
                             String mentorName, Long academicYearId, String academicYearLabel, Integer periodSeq,
                             LocalDate meetingDate, MeetingFormat format, boolean studentPresent,
                             boolean mentorPresent, String workActivities, String universityProgress,
                             String universityFeedback, String trainingOpportunities, String agreedActions,
                             boolean concernFlag, String concernNote, LocalDate nextMeetingDate,
                             MeetingStatus status, Instant submittedAt, Integer version, Instant createdAt,
                             Instant updatedAt, boolean deleted, Instant deletedAt, String deleteReason,
                             boolean canEdit, boolean canDelete, Instant editableUntil) {
    }

    public record MeetingSummary(Long id, Long studentId, String studentName, LocalDate meetingDate, String aosName,
                                 MeetingFormat format, boolean concernFlag, MeetingStatus status, Instant submittedAt,
                                 Instant deletedAt, String deleteReason) {
    }

    public record DeleteRequest(@Size(max = 500) String reason) {
    }

    public record RevisionDto(Long id, int version, Instant changedAt, Long changedBy, String changedByName,
                              String snapshotJson) {
    }

    public record DuplicateInfo(List<Long> existingMeetingIds) {
    }
}
