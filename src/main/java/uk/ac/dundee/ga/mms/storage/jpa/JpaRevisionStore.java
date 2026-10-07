package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.domain.MeetingRevision;
import uk.ac.dundee.ga.mms.storage.RevisionStore;

import java.util.List;

@Repository
public class JpaRevisionStore implements RevisionStore {
    private final RevisionRepository r;

    public JpaRevisionStore(RevisionRepository r) {
        this.r = r;
    }

    @Override
    public MeetingRevision save(MeetingRevision revision) {
        return r.save(revision);
    }

    @Override
    public List<MeetingRevision> findByMeeting(Long meetingId) {
        return r.findByMeetingIdOrderByVersionDesc(meetingId);
    }

    @Override
    @Transactional
    public void deleteByMeeting(Long meetingId) {
        r.deleteByMeetingId(meetingId);
    }
}
