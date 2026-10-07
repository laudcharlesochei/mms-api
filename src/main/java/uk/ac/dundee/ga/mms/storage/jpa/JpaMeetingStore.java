package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.MeetingStatus;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.storage.MeetingStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public class JpaMeetingStore extends JpaCrudStore<MentorMeeting> implements MeetingStore {
    private final MeetingRepository r;

    public JpaMeetingStore(MeetingRepository r) {
        super(r);
        this.r = r;
    }

    @Override
    public MentorMeeting save(MentorMeeting entity) {
        return r.saveAndFlush(entity);
    }

    @Override
    public List<MentorMeeting> findByStudent(Long studentId) {
        return r.findByStudentIdAndDeletedAtIsNull(studentId);
    }

    @Override
    public List<MentorMeeting> findSubmittedInYear(Long academicYearId) {
        return r.findByAcademicYearIdAndStatusAndDeletedAtIsNull(academicYearId, MeetingStatus.SUBMITTED);
    }

    @Override
    public List<MentorMeeting> findDraftsByAuthor(Long aosUserId) {
        return r.findByAosUserIdAndStatusAndDeletedAtIsNull(aosUserId, MeetingStatus.DRAFT);
    }

    @Override
    public List<MentorMeeting> findDeleted() {
        return r.findByDeletedAtIsNotNull();
    }

    @Override
    public List<MentorMeeting> findSubmittedByStudentAndDate(Long studentId, LocalDate date) {
        return r.findByStudentIdAndMeetingDateAndStatusAndDeletedAtIsNull(studentId, date, MeetingStatus.SUBMITTED);
    }

    @Override
    public Optional<MentorMeeting> findByClientRequestId(String clientRequestId) {
        return clientRequestId == null ? Optional.empty() : r.findFirstByClientRequestId(clientRequestId);
    }

    @Override
    public List<MentorMeeting> findAllForStudentIncludingDeleted(Long studentId) {
        return r.findByStudentId(studentId);
    }
}
