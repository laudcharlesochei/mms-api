package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AcademicYearDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AcademicYearRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.PeriodDto;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Academic years and their meeting periods (FR-05). */
@Service
public class AcademicYearService {

    private final AcademicYearStore years;

    public AcademicYearService(AcademicYearStore years) {
        this.years = years;
    }

    public List<AcademicYearDto> list() {
        return years.findAll().stream().sorted(Comparator.comparing(AcademicYear::getStartDate).reversed())
                .map(this::toDto).toList();
    }

    public AcademicYear current() {
        return years.findCurrent().orElseThrow(() -> ApiException.badRequest(
                "No current academic year is configured. An Administrator must create one."));
    }

    /** Resolve the year from an optional id, defaulting to the current year. */
    public AcademicYear resolve(Long yearId) {
        if (yearId == null) {
            return current();
        }
        return years.findById(yearId).orElseThrow(() -> ApiException.notFound("Academic year"));
    }

    public List<MeetingPeriod> periods(Long yearId) {
        return years.findPeriods(yearId);
    }

    public AcademicYearDto get(Long id) {
        return toDto(resolve(id));
    }

    @Transactional
    @Auditable(action = "ACADEMIC_YEAR_CREATE", entity = "AcademicYear")
    public AcademicYearDto create(AcademicYearRequest r) {
        validate(r, null);
        AcademicYear y = new AcademicYear();
        apply(y, r);
        y = years.save(y);
        generatePeriods(y);
        if (r.current()) {
            makeCurrent(y.getId());
        }
        return toDto(years.findById(y.getId()).orElseThrow());
    }

    @Transactional
    @Auditable(action = "ACADEMIC_YEAR_UPDATE", entity = "AcademicYear")
    public AcademicYearDto update(Long id, AcademicYearRequest r) {
        AcademicYear y = resolve(id);
        validate(r, id);
        boolean regenerate = y.getRequiredMeetings() != r.requiredMeetings()
                || !y.getStartDate().equals(r.startDate()) || !y.getEndDate().equals(r.endDate());
        apply(y, r);
        years.save(y);
        if (regenerate) {
            generatePeriods(y);
        }
        if (r.current()) {
            makeCurrent(id);
        }
        return toDto(years.findById(id).orElseThrow());
    }

    /** Replace the period boundaries of a year (they are configurable per year). */
    @Transactional
    @Auditable(action = "PERIODS_UPDATE", entity = "AcademicYear")
    public AcademicYearDto updatePeriods(Long id, List<PeriodDto> periods) {
        AcademicYear y = resolve(id);
        List<PeriodDto> sorted = periods.stream().sorted(Comparator.comparing(PeriodDto::startDate)).toList();
        LocalDate prevEnd = null;
        for (PeriodDto p : sorted) {
            if (p.startDate() == null || p.endDate() == null || p.endDate().isBefore(p.startDate())) {
                throw ApiException.badRequest("Each period needs a start date on or before its end date.");
            }
            if (!y.contains(p.startDate()) || !y.contains(p.endDate())) {
                throw ApiException.badRequest("Periods must fall inside the academic year.");
            }
            if (prevEnd != null && !p.startDate().isAfter(prevEnd)) {
                throw ApiException.badRequest("Periods must not overlap.");
            }
            prevEnd = p.endDate();
        }
        years.deletePeriods(id);
        int seq = 1;
        for (PeriodDto p : sorted) {
            MeetingPeriod mp = new MeetingPeriod();
            mp.setAcademicYearId(id);
            mp.setSeq(seq++);
            mp.setStartDate(p.startDate());
            mp.setEndDate(p.endDate());
            years.savePeriod(mp);
        }
        y.setRequiredMeetings(sorted.size());
        years.save(y);
        return toDto(y);
    }

    private void makeCurrent(Long id) {
        for (AcademicYear other : years.findAll()) {
            boolean shouldBe = other.getId().equals(id);
            if (other.isCurrent() != shouldBe) {
                other.setCurrent(shouldBe);
                years.save(other);
            }
        }
    }

    private void validate(AcademicYearRequest r, Long selfId) {
        if (r.label() == null || !r.label().matches("\\d{4}/\\d{2}")) {
            throw ApiException.validation(Map.of("label", "Use the form 2026/27"));
        }
        if (r.startDate() == null || r.endDate() == null || !r.endDate().isAfter(r.startDate())) {
            throw ApiException.validation(Map.of("endDate", "End date must be after start date"));
        }
        if (r.requiredMeetings() < 1 || r.requiredMeetings() > 12) {
            throw ApiException.validation(Map.of("requiredMeetings", "Between 1 and 12"));
        }
        years.findByLabel(r.label()).filter(y -> !y.getId().equals(selfId)).ifPresent(y -> {
            throw ApiException.conflict("duplicate", "An academic year with this label already exists");
        });
    }

    private void apply(AcademicYear y, AcademicYearRequest r) {
        y.setLabel(r.label());
        y.setStartDate(r.startDate());
        y.setEndDate(r.endDate());
        y.setRequiredMeetings(r.requiredMeetings());
    }

    /** Split the year evenly by the required count (whole months when they divide evenly). */
    void generatePeriods(AcademicYear y) {
        years.deletePeriods(y.getId());
        for (MeetingPeriod p : split(y.getId(), y.getStartDate(), y.getEndDate(), y.getRequiredMeetings())) {
            years.savePeriod(p);
        }
    }

    public static List<MeetingPeriod> split(Long yearId, LocalDate start, LocalDate end, int n) {
        List<MeetingPeriod> out = new ArrayList<>();
        LocalDate endExclusive = end.plusDays(1);
        long months = ChronoUnit.MONTHS.between(start, endExclusive);
        boolean monthAligned = start.getDayOfMonth() == 1 && endExclusive.getDayOfMonth() == 1
                && start.plusMonths(months).equals(endExclusive) && months % n == 0;
        long days = ChronoUnit.DAYS.between(start, endExclusive);
        for (int i = 0; i < n; i++) {
            LocalDate s;
            LocalDate e;
            if (monthAligned) {
                long step = months / n;
                s = start.plusMonths(step * i);
                e = start.plusMonths(step * (i + 1)).minusDays(1);
            } else {
                s = start.plusDays(days * i / n);
                e = start.plusDays(days * (i + 1) / n).minusDays(1);
            }
            MeetingPeriod p = new MeetingPeriod();
            p.setAcademicYearId(yearId);
            p.setSeq(i + 1);
            p.setStartDate(s);
            p.setEndDate(e);
            out.add(p);
        }
        return out;
    }

    public AcademicYearDto toDto(AcademicYear y) {
        List<PeriodDto> ps = years.findPeriods(y.getId()).stream()
                .map(p -> new PeriodDto(p.getId(), p.getSeq(), p.getStartDate(), p.getEndDate())).toList();
        return new AcademicYearDto(y.getId(), y.getLabel(), y.getStartDate(), y.getEndDate(),
                y.getRequiredMeetings(), y.isCurrent(), ps);
    }
}
