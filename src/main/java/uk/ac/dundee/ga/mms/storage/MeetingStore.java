package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.MentorMeeting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingStore extends CrudStore<MentorMeeting> {
    /** All non-deleted meetings for a student (drafts included; callers filter by visibility). */
    List<MentorMeeting> findByStudent(Long studentId);

    /** Submitted, non-deleted meetings in an academic year (dashboard source). */
    List<MentorMeeting> findSubmittedInYear(Long academicYearId);

    List<MentorMeeting> findDraftsByAuthor(Long aosUserId);

    List<MentorMeeting> findDeleted();

    List<MentorMeeting> findSubmittedByStudentAndDate(Long studentId, LocalDate date);

    Optional<MentorMeeting> findByClientRequestId(String clientRequestId);

    /** Including deleted - for subject-access export and erasure. */
    List<MentorMeeting> findAllForStudentIncludingDeleted(Long studentId);
}
