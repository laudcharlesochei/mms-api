package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.MeetingRevision;

import java.util.List;

public interface RevisionRepository extends JpaRepository<MeetingRevision, Long> {
    List<MeetingRevision> findByMeetingIdOrderByVersionDesc(Long meetingId);

    void deleteByMeetingId(Long meetingId);
}
