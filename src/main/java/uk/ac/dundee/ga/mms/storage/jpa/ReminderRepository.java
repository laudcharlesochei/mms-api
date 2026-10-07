package uk.ac.dundee.ga.mms.storage.jpa;

import org.springframework.data.jpa.repository.JpaRepository;
import uk.ac.dundee.ga.mms.domain.ReminderLog;

public interface ReminderRepository extends JpaRepository<ReminderLog, Long> {
    boolean existsByAosUserIdAndPeriodIdAndKind(Long aosUserId, Long periodId, String kind);
}
