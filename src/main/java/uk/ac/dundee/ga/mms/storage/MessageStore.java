package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.StaffMessage;

import java.util.List;
import java.util.Optional;

public interface MessageStore {
    StaffMessage save(StaffMessage m);

    Optional<StaffMessage> findById(Long id);

    List<StaffMessage> conversation(Long userA, Long userB);

    List<StaffMessage> findInvolving(Long userId);

    long unreadCount(Long recipientId);
}
