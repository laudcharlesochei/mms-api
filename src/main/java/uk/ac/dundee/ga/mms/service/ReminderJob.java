package uk.ac.dundee.ga.mms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.domain.ProgressStatus;
import uk.ac.dundee.ga.mms.domain.ReminderLog;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.storage.ReminderStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgressRow;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Weekly nudge to each Advisor of Studies listing advisees who still need a meeting this period
 * (adapted from the MentorSync progress-reminder command). Sent once per AoS per period and kind.
 * Off unless REMINDERS_ENABLED=true (and MAIL_ENABLED=true to actually send).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mms.reminders.enabled", havingValue = "true")
public class ReminderJob {

    private final AcademicYearStore years;
    private final DashboardService dashboard;
    private final ReminderStore reminders;
    private final UserStore users;
    private final MailService mail;
    private final MmsProperties props;

    public ReminderJob(AcademicYearStore years, DashboardService dashboard, ReminderStore reminders, UserStore users,
                       MailService mail, MmsProperties props) {
        this.years = years;
        this.dashboard = dashboard;
        this.reminders = reminders;
        this.users = users;
        this.mail = mail;
        this.props = props;
    }

    @Scheduled(cron = "${mms.reminders.cron}", zone = "${mms.reminders.zone}")
    public void run() {
        Optional<AcademicYear> y = years.findCurrent();
        if (y.isEmpty()) {
            return;
        }
        LocalDate today = dashboard.today();
        Optional<MeetingPeriod> period = years.findPeriods(y.get().getId()).stream().filter(p -> p.contains(today)).findFirst();
        if (period.isEmpty()) {
            return;
        }
        MeetingPeriod p = period.get();
        long daysLeft = ChronoUnit.DAYS.between(today, p.getEndDate());
        String kind = daysLeft <= props.getReminders().getDueWindowDays() ? "DUE_SOON" : "OVERDUE";
        Map<Long, List<ProgressRow>> behind = dashboard.allRows(y.get()).stream()
                .filter(r -> r.status() == ProgressStatus.OVERDUE
                        || (r.status() == ProgressStatus.DUE && "DUE_SOON".equals(kind)))
                .collect(Collectors.groupingBy(ProgressRow::aosUserId));
        behind.forEach((aosId, rows) -> {
            if (reminders.exists(aosId, p.getId(), kind)) {
                return;
            }
            users.findById(aosId).filter(u -> u.isActive()).ifPresent(aos -> {
                mail.reminder(aos, "Period " + p.getSeq() + " (" + p.getStartDate() + " to " + p.getEndDate() + ")", rows);
                ReminderLog entry = new ReminderLog();
                entry.setAosUserId(aosId);
                entry.setPeriodId(p.getId());
                entry.setKind(kind);
                entry.setSentAt(Instant.now());
                reminders.save(entry);
            });
        });
        log.info("Reminder run complete: {} advisor(s) with advisees behind", behind.size());
    }
}
