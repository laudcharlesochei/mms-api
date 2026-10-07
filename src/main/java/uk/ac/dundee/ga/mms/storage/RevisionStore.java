package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.MeetingRevision;

import java.util.List;

public interface RevisionStore {
    MeetingRevision save(MeetingRevision revision);

    List<MeetingRevision> findByMeeting(Long meetingId);

    void deleteByMeeting(Long meetingId);
}
