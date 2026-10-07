package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.ProgressStatus;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.domain.StudentStatus;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.MeetingStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.util.Csv;
import uk.ac.dundee.ga.mms.web.dto.DashboardDtos.AdvisorRow;
import uk.ac.dundee.ga.mms.web.dto.DashboardDtos.Summary;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgressRow;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Dashboard and tracking (FR-21 to FR-24). Counts are computed on read from submitted,
 * non-deleted meetings - no counters are stored, so they cannot drift.
 */
@Service
public class DashboardService {

    public static final ZoneId UK = ZoneId.of("Europe/London");

    private final AcademicYearService years;
    private final AllocationStore allocations;
    private final StudentStore students;
    private final MeetingStore meetings;
    private final ScopeService scope;
    private final Lookups lookups;
    private final Clock clock;

    public DashboardService(AcademicYearService years, AllocationStore allocations, StudentStore students,
                            MeetingStore meetings, ScopeService scope, Lookups lookups, Clock clock) {
        this.years = years;
        this.allocations = allocations;
        this.students = students;
        this.meetings = meetings;
        this.scope = scope;
        this.lookups = lookups;
        this.clock = clock;
    }

    public LocalDate today() {
        return LocalDate.now(clock.withZone(UK));
    }

    /** Filters for the per-student table; status may also be "ZERO" (no meetings yet). */
    public record Filter(Long programmeId, Long aosUserId, String status, String q) {
        public static Filter none() {
            return new Filter(null, null, null, null);
        }
    }

    /** All progress rows for the year (unscoped). */
    public List<ProgressRow> allRows(AcademicYear year) {
        LocalDate today = today();
        List<MeetingPeriod> periods = years.periods(year.getId());
        Map<Long, AdvisorAllocation> alloc = latestAllocationPerStudent(year.getId());
        Map<Long, List<MentorMeeting>> byStudent = meetings.findSubmittedInYear(year.getId()).stream()
                .collect(Collectors.groupingBy(MentorMeeting::getStudentId));
        Map<Long, Student> studentMap = students.findByIds(alloc.keySet()).stream()
                .collect(Collectors.toMap(Student::getId, s -> s));
        Lookups.Snapshot s = lookups.snapshot();
        List<ProgressRow> rows = new ArrayList<>();
        for (AdvisorAllocation a : alloc.values()) {
            Student st = studentMap.get(a.getStudentId());
            if (st == null || st.getStatus() == StudentStatus.WITHDRAWN) {
                continue;
            }
            List<MentorMeeting> ms = byStudent.getOrDefault(st.getId(), List.of());
            LocalDate last = ms.stream().map(MentorMeeting::getMeetingDate).max(Comparator.naturalOrder()).orElse(null);
            ProgressStatus status = StatusCalculator.status(periods, year.getRequiredMeetings(), ms, today);
            rows.add(new ProgressRow(st.getId(), st.getMatricNo(), st.fullName(), st.getProgrammeId(),
                    s.programmeCode(st.getProgrammeId()), s.programmeName(st.getProgrammeId()), a.getAosUserId(),
                    s.userName(a.getAosUserId()), s.employerName(st.getEmployerId()), s.mentorName(st.getMentorId()),
                    ms.size(), year.getRequiredMeetings(), last, status, status == ProgressStatus.CONCERN,
                    st.getStatus()));
        }
        rows.sort(Comparator.comparing(ProgressRow::studentName, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    /** Rows the user may see, after filters. */
    public List<ProgressRow> rows(CurrentUser u, AcademicYear year, Filter f) {
        Set<Long> visible = scope.visibleStudentIds(u);
        String needle = f.q() == null ? null : f.q().trim().toLowerCase(Locale.ROOT);
        return allRows(year).stream()
                .filter(r -> visible == null || visible.contains(r.studentId()))
                .filter(r -> f.programmeId() == null || f.programmeId().equals(r.programmeId()))
                .filter(r -> f.aosUserId() == null || f.aosUserId().equals(r.aosUserId()))
                .filter(r -> matchesStatus(r, f.status()))
                .filter(r -> needle == null || needle.isEmpty() || r.studentName().toLowerCase(Locale.ROOT).contains(needle)
                        || r.matricNo().toLowerCase(Locale.ROOT).contains(needle))
                .toList();
    }

    private static boolean matchesStatus(ProgressRow r, String status) {
        if (status == null || status.isBlank()) {
            return true;
        }
        return switch (status.toUpperCase(Locale.ROOT)) {
            case "ZERO" -> r.meetingsDone() == 0;
            case "BEHIND" -> r.status() == ProgressStatus.OVERDUE || r.status() == ProgressStatus.DUE;
            default -> r.status().name().equalsIgnoreCase(status);
        };
    }

    public Summary summary(AcademicYear year, List<ProgressRow> rows) {
        int required = rows.stream().mapToInt(ProgressRow::meetingsRequired).sum();
        int done = rows.stream().mapToInt(ProgressRow::meetingsDone).sum();
        int doneCapped = rows.stream().mapToInt(r -> Math.min(r.meetingsDone(), r.meetingsRequired())).sum();
        double pct = required == 0 ? 0 : Math.round(doneCapped * 1000.0 / required) / 10.0;
        LocalDate today = today();
        Integer currentSeq = years.periods(year.getId()).stream().filter(p -> p.contains(today))
                .map(MeetingPeriod::getSeq).findFirst().orElse(null);
        return new Summary(year.getId(), year.getLabel(), currentSeq, rows.size(), required, done, pct,
                (int) rows.stream().filter(r -> r.meetingsDone() == 0).count(),
                (int) rows.stream().filter(ProgressRow::openConcern).count(),
                (int) rows.stream().filter(r -> r.status() == ProgressStatus.OVERDUE).count(),
                (int) rows.stream().filter(r -> r.status() == ProgressStatus.DUE).count(),
                (int) rows.stream().filter(r -> r.status() == ProgressStatus.ON_TRACK).count());
    }

    public List<AdvisorRow> advisors(List<ProgressRow> rows) {
        Lookups.Snapshot s = lookups.snapshot();
        Map<Long, List<ProgressRow>> byAos = rows.stream().collect(Collectors.groupingBy(ProgressRow::aosUserId,
                LinkedHashMap::new, Collectors.toList()));
        List<AdvisorRow> out = new ArrayList<>();
        byAos.forEach((aosId, list) -> out.add(new AdvisorRow(aosId, s.userName(aosId), s.userEmail(aosId), list.size(),
                list.stream().mapToInt(ProgressRow::meetingsDone).sum(),
                list.stream().mapToInt(ProgressRow::meetingsRequired).sum(),
                (int) list.stream().filter(r -> r.status() == ProgressStatus.OVERDUE).count(),
                (int) list.stream().filter(r -> r.status() == ProgressStatus.DUE).count(),
                (int) list.stream().filter(ProgressRow::openConcern).count())));
        out.sort(Comparator.comparing(AdvisorRow::aosName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        return out;
    }

    /** Current advisees of the AoS (open allocations in the year) with their progress - screen S2. */
    public List<ProgressRow> advisees(CurrentUser u, AcademicYear year) {
        Set<Long> mine = allocations.findByAcademicYear(year.getId()).stream()
                .filter(a -> a.isOpen() && u.id().equals(a.getAosUserId()))
                .map(AdvisorAllocation::getStudentId).collect(Collectors.toSet());
        return allRows(year).stream().filter(r -> mine.contains(r.studentId())).toList();
    }

    public Optional<ProgressRow> progressFor(AcademicYear year, Long studentId) {
        return allRows(year).stream().filter(r -> r.studentId().equals(studentId)).findFirst();
    }

    public String exportCsv(List<ProgressRow> rows) {
        StringBuilder sb = new StringBuilder(Csv.row(List.of("student_name", "matric_no", "programme", "advisor_of_studies",
                "meetings_done", "meetings_required", "last_meeting_date", "status")));
        for (ProgressRow r : rows) {
            sb.append(Csv.row(Arrays.asList(r.studentName(), r.matricNo(), r.programmeName(), r.aosName(),
                    r.meetingsDone(), r.meetingsRequired(), r.lastMeetingDate(), r.status())));
        }
        return sb.toString();
    }

    /** The open allocation per student, or the most recent one when the year's allocations are closed. */
    private Map<Long, AdvisorAllocation> latestAllocationPerStudent(Long yearId) {
        Map<Long, AdvisorAllocation> m = new HashMap<>();
        for (AdvisorAllocation a : allocations.findByAcademicYear(yearId)) {
            AdvisorAllocation cur = m.get(a.getStudentId());
            if (cur == null || (a.isOpen() && !cur.isOpen())
                    || (a.isOpen() == cur.isOpen() && a.getValidFrom().isAfter(cur.getValidFrom()))) {
                m.put(a.getStudentId(), a);
            }
        }
        return m;
    }
}
