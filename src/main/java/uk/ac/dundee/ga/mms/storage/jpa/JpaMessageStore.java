package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.StaffMessage;
import uk.ac.dundee.ga.mms.storage.MessageStore;

import java.util.List;
import java.util.Optional;

@Repository
public class JpaMessageStore implements MessageStore {
    private final MessageRepository r;

    public JpaMessageStore(MessageRepository r) {
        this.r = r;
    }

    @Override
    public StaffMessage save(StaffMessage m) {
        return r.save(m);
    }

    @Override
    public Optional<StaffMessage> findById(Long id) {
        return r.findById(id);
    }

    @Override
    public List<StaffMessage> conversation(Long a, Long b) {
        return r.conversation(a, b);
    }

    @Override
    public List<StaffMessage> findInvolving(Long userId) {
        return r.findBySenderIdOrRecipientIdOrderByCreatedAtDesc(userId, userId);
    }

    @Override
    public long unreadCount(Long recipientId) {
        return r.countByRecipientIdAndReadAtIsNull(recipientId);
    }
}
