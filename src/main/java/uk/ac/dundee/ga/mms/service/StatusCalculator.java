package uk.ac.dundee.ga.mms.service;

import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.ProgressStatus;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Status rules (FR-22): Concern = latest submitted meeting flagged; Overdue = an ended period
 * with no submitted meeting; Due = the current period has no meeting yet; otherwise On track.
 * A student who already has the required number of meetings is On track.
 */
public final class StatusCalculator {

    private StatusCalculator() {
    }

    public static ProgressStatus status(List<MeetingPeriod> periods, int required,
                                        List<MentorMeeting> submitted, LocalDate today) {
        MentorMeeting latest = submitted.stream()
                .max(Comparator.comparing(MentorMeeting::getMeetingDate).thenComparing(MentorMeeting::getId,
                        Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
        if (latest != null && latest.isConcernFlag()) {
            return ProgressStatus.CONCERN;
        }
        if (required > 0 && submitted.size() >= required) {
            return ProgressStatus.ON_TRACK;
        }
        if (periods == null || periods.isEmpty()) {
            return submitted.isEmpty() ? ProgressStatus.DUE : ProgressStatus.ON_TRACK;
        }
        boolean due = false;
        for (MeetingPeriod p : periods) {
            boolean hasMeeting = submitted.stream().anyMatch(m -> p.getId() != null && p.getId().equals(m.getPeriodId())
                    || p.contains(m.getMeetingDate()));
            if (p.getEndDate().isBefore(today) && !hasMeeting) {
                return ProgressStatus.OVERDUE;
            }
            if (p.contains(today) && !hasMeeting) {
                due = true;
            }
        }
        return due ? ProgressStatus.DUE : ProgressStatus.ON_TRACK;
    }
}
