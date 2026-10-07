package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.stereotype.Repository;
import uk.ac.dundee.ga.mms.domain.ReminderLog;
import uk.ac.dundee.ga.mms.storage.ReminderStore;

@Repository
public class JpaReminderStore implements ReminderStore {
    private final ReminderRepository r;

    public JpaReminderStore(ReminderRepository r) {
        this.r = r;
    }

    @Override
    public boolean exists(Long aosUserId, Long periodId, String kind) {
        return r.existsByAosUserIdAndPeriodIdAndKind(aosUserId, periodId, kind);
    }

    @Override
    public ReminderLog save(ReminderLog log) {
        return r.save(log);
    }
}
