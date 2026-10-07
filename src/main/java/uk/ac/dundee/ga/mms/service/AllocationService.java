package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.AllocationSource;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AllocationDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ReassignRequest;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.time.LocalDate;
import java.util.Map;

/** Advisor allocations and mid-year reassignment with history (FR-09). */
@Service
public class AllocationService {

    private final AllocationStore allocations;
    private final StudentStore students;
    private final UserStore users;
    private final AcademicYearService years;
    private final ScopeService scope;
    private final Lookups lookups;
    private final ReferenceDataService reference;
    private final AuditService audit;

    public AllocationService(AllocationStore allocations, StudentStore students, UserStore users,
                             AcademicYearService years, ScopeService scope, Lookups lookups,
                             ReferenceDataService reference, AuditService audit) {
        this.allocations = allocations;
        this.students = students;
        this.users = users;
        this.years = years;
        this.scope = scope;
        this.lookups = lookups;
        this.reference = reference;
        this.audit = audit;
    }

    @Transactional
    public AllocationDto reassign(CurrentUser u, Long studentId, ReassignRequest r) {
        Student st = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (!scope.canManageProgramme(u, st.getProgrammeId())) {
            throw ApiException.forbidden("You can only manage students on your own programme.");
        }
        AppUser aos = users.findById(r.aosUserId()).orElseThrow(() -> ApiException.badRequest("Advisor not found"));
        if (!aos.isActive() || !(aos.hasRole(Role.AOS) || aos.hasRole(Role.DIRECTOR) || aos.hasRole(Role.PROG_LEAD))) {
            throw ApiException.badRequest("The chosen user is not an active Advisor of Studies");
        }
        AcademicYear year = years.resolve(r.academicYearId());
        LocalDate from = r.effectiveFrom() == null ? LocalDate.now() : r.effectiveFrom();
        AdvisorAllocation created = allocate(studentId, aos.getId(), year, from, AllocationSource.MANUAL);
        audit.record("ALLOCATION_REASSIGN", "Student", studentId, Map.of("aosUserId", aos.getId(), "year", year.getLabel()));
        return reference.toDto(created, lookups.snapshot());
    }

    /**
     * Opens an allocation for the student in the year, closing any different open one (history kept).
     * Returns the open allocation (existing one if the AoS is unchanged).
     */
    public AdvisorAllocation allocate(Long studentId, Long aosUserId, AcademicYear year, LocalDate from, AllocationSource source) {
        var open = allocations.findOpen(studentId, year.getId());
        if (open.isPresent()) {
            if (open.get().getAosUserId().equals(aosUserId)) {
                return open.get();
            }
            AdvisorAllocation old = open.get();
            LocalDate end = from.minusDays(1);
            old.setValidTo(end.isBefore(old.getValidFrom()) ? old.getValidFrom() : end);
            allocations.save(old);
        }
        LocalDate start = from.isBefore(year.getStartDate()) ? year.getStartDate() : from;
        AdvisorAllocation a = new AdvisorAllocation();
        a.setStudentId(studentId);
        a.setAosUserId(aosUserId);
        a.setAcademicYearId(year.getId());
        a.setValidFrom(start);
        a.setSource(source);
        return allocations.save(a);
    }

    /** Close open allocations of earlier academic years for the student (September roll-over). */
    public void closePreviousYears(Long studentId, AcademicYear year) {
        for (AdvisorAllocation a : allocations.findByStudent(studentId)) {
            if (a.isOpen() && !a.getAcademicYearId().equals(year.getId()) && a.getValidFrom().isBefore(year.getStartDate())) {
                a.setValidTo(year.getStartDate().minusDays(1));
                allocations.save(a);
            }
        }
    }
}
