package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.MeetingStatus;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MeetingRepository extends JpaRepository<MentorMeeting, Long> {
    List<MentorMeeting> findByStudentIdAndDeletedAtIsNull(Long studentId);

    List<MentorMeeting> findByAcademicYearIdAndStatusAndDeletedAtIsNull(Long academicYearId, MeetingStatus status);

    List<MentorMeeting> findByAosUserIdAndStatusAndDeletedAtIsNull(Long aosUserId, MeetingStatus status);

    List<MentorMeeting> findByDeletedAtIsNotNull();

    List<MentorMeeting> findByStudentIdAndMeetingDateAndStatusAndDeletedAtIsNull(Long studentId, LocalDate date, MeetingStatus status);

    Optional<MentorMeeting> findFirstByClientRequestId(String clientRequestId);

    List<MentorMeeting> findByStudentId(Long studentId);
}
