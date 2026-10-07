package uk.ac.dundee.ga.mms;

import org.junit.jupiter.api.Test;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.ProgressStatus;
import uk.ac.dundee.ga.mms.service.AcademicYearService;
import uk.ac.dundee.ga.mms.service.StatusCalculator;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StatusCalculatorTest {

    private final List<MeetingPeriod> periods = periods();

    private static List<MeetingPeriod> periods() {
        List<MeetingPeriod> ps = AcademicYearService.split(1L, LocalDate.of(2026, 9, 1), LocalDate.of(2027, 8, 31), 3);
        long id = 10;
        for (MeetingPeriod p : ps) {
            p.setId(id++);
        }
        return ps;
    }

    private static MentorMeeting meeting(LocalDate d, boolean concern, Long periodId) {
        MentorMeeting m = new MentorMeeting();
        m.setId(d.toEpochDay());
        m.setMeetingDate(d);
        m.setConcernFlag(concern);
        m.setPeriodId(periodId);
        return m;
    }

    @Test
    void splitsYearIntoWholeMonthPeriods() {
        assertThat(periods).hasSize(3);
        assertThat(periods.get(0).getStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(periods.get(0).getEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
        assertThat(periods.get(1).getStartDate()).isEqualTo(LocalDate.of(2027, 1, 1));
        assertThat(periods.get(2).getEndDate()).isEqualTo(LocalDate.of(2027, 8, 31));
    }

    @Test
    void dueWhenCurrentPeriodHasNoMeeting() {
        assertThat(StatusCalculator.status(periods, 3, List.of(), LocalDate.of(2026, 10, 4))).isEqualTo(ProgressStatus.DUE);
    }

    @Test
    void onTrackWhenCurrentPeriodHasMeeting() {
        var ms = List.of(meeting(LocalDate.of(2026, 9, 20), false, 10L));
        assertThat(StatusCalculator.status(periods, 3, ms, LocalDate.of(2026, 10, 4))).isEqualTo(ProgressStatus.ON_TRACK);
    }

    @Test
    void overdueWhenEndedPeriodHasNoMeeting() {
        var ms = List.of(meeting(LocalDate.of(2027, 1, 20), false, 11L));
        assertThat(StatusCalculator.status(periods, 3, ms, LocalDate.of(2027, 2, 1))).isEqualTo(ProgressStatus.OVERDUE);
    }

    @Test
    void meetingOnLastDayOfPeriodCounts() {
        var ms = List.of(meeting(LocalDate.of(2026, 12, 31), false, null));
        assertThat(StatusCalculator.status(periods, 3, ms, LocalDate.of(2027, 1, 2))).isEqualTo(ProgressStatus.DUE);
    }

    @Test
    void concernWhenLatestMeetingFlagged() {
        var ms = List.of(meeting(LocalDate.of(2026, 9, 2), false, 10L), meeting(LocalDate.of(2026, 9, 30), true, 10L));
        assertThat(StatusCalculator.status(periods, 3, ms, LocalDate.of(2026, 10, 4))).isEqualTo(ProgressStatus.CONCERN);
    }

    @Test
    void onTrackWhenRequiredCountReached() {
        var ms = List.of(meeting(LocalDate.of(2026, 9, 2), false, 10L), meeting(LocalDate.of(2026, 9, 9), false, 10L),
                meeting(LocalDate.of(2026, 9, 16), false, 10L));
        assertThat(StatusCalculator.status(periods, 3, ms, LocalDate.of(2027, 6, 1))).isEqualTo(ProgressStatus.ON_TRACK);
    }
}
