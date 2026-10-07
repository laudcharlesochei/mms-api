package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.Programme;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.ProgrammeStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data-scope rules from the permissions matrix (Section 3.3), applied in the service layer on
 * every call: ADMIN/DIRECTOR see all; PROG_LEAD sees their programme; AOS sees their advisees;
 * STUDENT sees only their own record. Deny by default.
 */
@Service
public class ScopeService {

    private final StudentStore students;
    private final ProgrammeStore programmes;
    private final AllocationStore allocations;
    private final AuditService audit;

    public ScopeService(StudentStore students, ProgrammeStore programmes, AllocationStore allocations, AuditService audit) {
        this.students = students;
        this.programmes = programmes;
        this.allocations = allocations;
        this.audit = audit;
    }

    /** Student ids the user may read, or {@code null} meaning "all". */
    public Set<Long> visibleStudentIds(CurrentUser u) {
        if (u.seesAll()) {
            return null;
        }
        Set<Long> ids = new HashSet<>();
        if (u.has(Role.PROG_LEAD)) {
            Set<Long> led = ledProgrammeIds(u);
            if (!led.isEmpty()) {
                students.findAll().stream().filter(s -> led.contains(s.getProgrammeId())).forEach(s -> ids.add(s.getId()));
            }
        }
        if (u.canAdvise()) {
            allocations.findByAos(u.id()).forEach(a -> ids.add(a.getStudentId()));
        }
        if (u.has(Role.STUDENT)) {
            students.findByUniEmail(u.email()).ifPresent(s -> ids.add(s.getId()));
        }
        return ids;
    }

    public Set<Long> ledProgrammeIds(CurrentUser u) {
        if (!u.has(Role.PROG_LEAD)) {
            return Set.of();
        }
        return programmes.findByLeadUserId(u.id()).stream().map(Programme::getId).collect(Collectors.toSet());
    }

    public boolean canView(CurrentUser u, Long studentId) {
        Set<Long> ids = visibleStudentIds(u);
        return ids == null || ids.contains(studentId);
    }

    public Student requireVisibleStudent(CurrentUser u, Long studentId) {
        Student s = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        if (!canView(u, studentId)) {
            audit.record("ACCESS_DENIED", "Student", studentId, Map.of("reason", "out of scope"));
            throw ApiException.forbidden("This student is outside your access scope.");
        }
        return s;
    }

    /** Admin/Director manage all; a Programme Lead manages students of programmes they lead. */
    public boolean canManageProgramme(CurrentUser u, Long programmeId) {
        return u.seesAll() || ledProgrammeIds(u).contains(programmeId);
    }

    /** True when the user is the student's open (current) Advisor of Studies in the year. */
    public boolean isCurrentAdvisor(CurrentUser u, Long studentId, Long academicYearId) {
        return allocations.findOpen(studentId, academicYearId)
                .map(AdvisorAllocation::getAosUserId).filter(u.id()::equals).isPresent();
    }
}
