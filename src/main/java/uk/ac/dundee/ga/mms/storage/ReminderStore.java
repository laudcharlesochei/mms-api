package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.ReminderLog;

public interface ReminderStore {
    boolean exists(Long aosUserId, Long periodId, String kind);

    ReminderLog save(ReminderLog log);
}
