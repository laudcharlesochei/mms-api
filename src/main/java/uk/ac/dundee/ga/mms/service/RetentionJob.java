package uk.ac.dundee.ga.mms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.domain.StudentStatus;
import uk.ac.dundee.ga.mms.storage.MeetingStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Retention purge (NFR-10): once a student has completed or withdrawn and their last meeting is
 * older than RETENTION_YEARS, their meeting records are erased. Off unless RETENTION_ENABLED=true.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mms.retention.enabled", havingValue = "true")
public class RetentionJob {

    private final StudentStore students;
    private final MeetingStore meetings;
    private final SubjectAccessService subjectAccess;
    private final AuditService audit;
    private final MmsProperties props;

    public RetentionJob(StudentStore students, MeetingStore meetings, SubjectAccessService subjectAccess,
                        AuditService audit, MmsProperties props) {
        this.students = students;
        this.meetings = meetings;
        this.subjectAccess = subjectAccess;
        this.audit = audit;
        this.props = props;
    }

    @Scheduled(cron = "${mms.retention.cron}", zone = "Europe/London")
    public void run() {
        LocalDate cutoff = LocalDate.now(DashboardService.UK).minusYears(props.getRetention().getYears());
        int purged = 0;
        for (Student s : students.findAll()) {
            if (s.getStatus() != StudentStatus.COMPLETED && s.getStatus() != StudentStatus.WITHDRAWN) {
                continue;
            }
            List<MentorMeeting> ms = meetings.findAllForStudentIncludingDeleted(s.getId());
            LocalDate last = ms.stream().map(MentorMeeting::getMeetingDate).max(LocalDate::compareTo).orElse(null);
            if (last != null && last.isBefore(cutoff)) {
                purged += subjectAccess.eraseMeetings(s.getId());
            }
        }
        audit.record(null, "system", "RETENTION_PURGE", "MentorMeeting", null, Map.of("purged", purged));
        log.info("Retention purge removed {} meeting record(s)", purged);
    }
}
