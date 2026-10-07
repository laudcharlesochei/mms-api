package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.Employer;
import uk.ac.dundee.ga.mms.domain.Programme;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.domain.StudentStatus;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.EmployerStore;
import uk.ac.dundee.ga.mms.storage.MentorStore;
import uk.ac.dundee.ga.mms.storage.PageResult;
import uk.ac.dundee.ga.mms.storage.ProgrammeStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.util.TextSanitizer;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AllocationDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.EmployerDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.EmployerRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.MentorDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.MentorRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgrammeDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ProgrammeRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.StudentDetail;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.StudentRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.StudentSummary;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Programmes, employers, workplace mentors and students (FR-06). */
@Service
public class ReferenceDataService {

    private final ProgrammeStore programmes;
    private final EmployerStore employers;
    private final MentorStore mentors;
    private final StudentStore students;
    private final AllocationStore allocations;
    private final AcademicYearStore years;
    private final UserStore users;
    private final ScopeService scope;
    private final Lookups lookups;
    private final DashboardService dashboard;

    public ReferenceDataService(ProgrammeStore programmes, EmployerStore employers, MentorStore mentors,
                                StudentStore students, AllocationStore allocations, AcademicYearStore years,
                                UserStore users, ScopeService scope, Lookups lookups, DashboardService dashboard) {
        this.programmes = programmes;
        this.employers = employers;
        this.mentors = mentors;
        this.students = students;
        this.allocations = allocations;
        this.years = years;
        this.users = users;
        this.scope = scope;
        this.lookups = lookups;
        this.dashboard = dashboard;
    }

    // ---------------------------------------------------------------- programmes

    public List<ProgrammeDto> programmes() {
        Lookups.Snapshot s = lookups.snapshot();
        Map<Long, Long> counts = students.findAll().stream()
                .collect(Collectors.groupingBy(Student::getProgrammeId, Collectors.counting()));
        return programmes.findAll().stream().sorted(Comparator.comparing(Programme::getName))
                .map(p -> new ProgrammeDto(p.getId(), p.getCode(), p.getName(), p.getLeadUserId(),
                        s.userName(p.getLeadUserId()), p.isActive(), counts.getOrDefault(p.getId(), 0L)))
                .toList();
    }

    @Transactional
    @Auditable(action = "PROGRAMME_SAVE", entity = "Programme")
    public Programme saveProgramme(Long id, ProgrammeRequest r) {
        Programme p = id == null ? new Programme() : programmes.findById(id).orElseThrow(() -> ApiException.notFound("Programme"));
        String code = r.code().trim().toUpperCase(Locale.ROOT);
        programmes.findByCode(code).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("duplicate", "Programme code already exists");
        });
        if (r.leadUserId() != null && users.findById(r.leadUserId()).isEmpty()) {
            throw ApiException.badRequest("Programme lead user not found");
        }
        p.setCode(code);
        p.setName(TextSanitizer.clean(r.name()));
        p.setLeadUserId(r.leadUserId());
        if (r.active() != null) {
            p.setActive(r.active());
        }
        return programmes.save(p);
    }

    // ---------------------------------------------------------------- employers & mentors

    public List<EmployerDto> employers() {
        return employers.findAll().stream().sorted(Comparator.comparing(Employer::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::toDto).toList();
    }

    @Transactional
    @Auditable(action = "EMPLOYER_SAVE", entity = "Employer")
    public Employer saveEmployer(Long id, EmployerRequest r) {
        Employer e = id == null ? new Employer() : employers.findById(id).orElseThrow(() -> ApiException.notFound("Employer"));
        String name = TextSanitizer.clean(r.name());
        employers.findByName(name).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("duplicate", "An employer with this name already exists");
        });
        e.setName(name);
        e.setAddress(TextSanitizer.clean(r.address()));
        if (r.active() != null) {
            e.setActive(r.active());
        }
        return employers.save(e);
    }

    public List<MentorDto> mentors(Long employerId) {
        Lookups.Snapshot s = lookups.snapshot();
        List<WorkplaceMentor> list = employerId == null ? mentors.findAll() : mentors.findByEmployerId(employerId);
        return list.stream().sorted(Comparator.comparing(WorkplaceMentor::getFullName, String.CASE_INSENSITIVE_ORDER))
                .map(m -> toDto(m, s)).toList();
    }

    @Transactional
    @Auditable(action = "MENTOR_SAVE", entity = "WorkplaceMentor")
    public WorkplaceMentor saveMentor(Long id, MentorRequest r) {
        WorkplaceMentor m = id == null ? new WorkplaceMentor() : mentors.findById(id).orElseThrow(() -> ApiException.notFound("Mentor"));
        employers.findById(r.employerId()).orElseThrow(() -> ApiException.badRequest("Employer not found"));
        m.setEmployerId(r.employerId());
        m.setFullName(TextSanitizer.clean(r.fullName()));
        m.setEmail(blankToNull(r.email()));
        m.setPhone(TextSanitizer.clean(r.phone()));
        if (r.active() != null) {
            m.setActive(r.active());
        }
        return mentors.save(m);
    }

    // ---------------------------------------------------------------- students

    public PageResult<StudentSummary> searchStudents(CurrentUser u, Long programmeId, Long aosId, StudentStatus status,
                                                     String q, int page, int size) {
        Set<Long> visible = scope.visibleStudentIds(u);
        Lookups.Snapshot s = lookups.snapshot();
        Map<Long, AdvisorAllocation> current = currentAllocations();
        String needle = q == null ? null : q.trim().toLowerCase(Locale.ROOT);
        List<StudentSummary> rows = students.findAll().stream()
                .filter(st -> visible == null || visible.contains(st.getId()))
                .filter(st -> programmeId == null || programmeId.equals(st.getProgrammeId()))
                .filter(st -> status == null || status == st.getStatus())
                .filter(st -> aosId == null || (current.get(st.getId()) != null && aosId.equals(current.get(st.getId()).getAosUserId())))
                .filter(st -> needle == null || needle.isEmpty()
                        || st.fullName().toLowerCase(Locale.ROOT).contains(needle)
                        || st.getMatricNo().toLowerCase(Locale.ROOT).contains(needle)
                        || (st.getUniEmail() != null && st.getUniEmail().toLowerCase(Locale.ROOT).contains(needle)))
                .sorted(Comparator.comparing(Student::getLastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(Student::getFirstName, String.CASE_INSENSITIVE_ORDER))
                .map(st -> toSummary(st, s, current.get(st.getId())))
                .toList();
        return PageResult.of(rows, page, size);
    }

    public StudentDetail studentDetail(CurrentUser u, Long id) {
        Student st = scope.requireVisibleStudent(u, id);
        Lookups.Snapshot s = lookups.snapshot();
        List<AllocationDto> history = allocations.findByStudent(id).stream()
                .sorted(Comparator.comparing(AdvisorAllocation::getValidFrom).reversed())
                .map(a -> toDto(a, s)).toList();
        AdvisorAllocation cur = currentAllocations().get(id);
        WorkplaceMentor mentor = st.getMentorId() == null ? null : s.mentors().get(st.getMentorId());
        Employer employer = st.getEmployerId() == null ? null : s.employers().get(st.getEmployerId());
        List<MentorDto> employerMentors = st.getEmployerId() == null ? List.of()
                : mentors.findByEmployerId(st.getEmployerId()).stream().filter(WorkplaceMentor::isActive)
                .map(m -> toDto(m, s)).toList();
        return new StudentDetail(toSummary(st, s, cur), mentor == null ? null : toDto(mentor, s),
                employer == null ? null : toDto(employer), history,
                years.findCurrent().flatMap(y -> dashboard.progressFor(y, id)).orElse(null),
                employerMentors);
    }

    @Transactional
    @Auditable(action = "STUDENT_SAVE", entity = "Student")
    public Student saveStudent(CurrentUser u, Long id, StudentRequest r) {
        Student st = id == null ? new Student() : students.findById(id).orElseThrow(() -> ApiException.notFound("Student"));
        if (id != null && !scope.canManageProgramme(u, st.getProgrammeId())) {
            throw ApiException.forbidden("You can only manage students on your own programme.");
        }
        if (!scope.canManageProgramme(u, r.programmeId())) {
            throw ApiException.forbidden("You can only manage students on your own programme.");
        }
        programmes.findById(r.programmeId()).orElseThrow(() -> ApiException.badRequest("Programme not found"));
        String matric = r.matricNo().trim().toUpperCase(Locale.ROOT);
        students.findByMatricNo(matric).filter(o -> !o.getId().equals(id)).ifPresent(o -> {
            throw ApiException.conflict("duplicate", "A student with this matriculation number already exists");
        });
        if (r.employerId() != null) {
            employers.findById(r.employerId()).orElseThrow(() -> ApiException.badRequest("Employer not found"));
        }
        if (r.mentorId() != null) {
            WorkplaceMentor m = mentors.findById(r.mentorId()).orElseThrow(() -> ApiException.badRequest("Mentor not found"));
            if (r.employerId() != null && !r.employerId().equals(m.getEmployerId())) {
                throw ApiException.badRequest("The mentor must work for the student's employer");
            }
        }
        st.setMatricNo(matric);
        st.setFirstName(TextSanitizer.clean(r.firstName()));
        st.setLastName(TextSanitizer.clean(r.lastName()));
        st.setUniEmail(blankToNull(r.uniEmail()));
        st.setProgrammeId(r.programmeId());
        st.setEmployerId(r.employerId());
        st.setMentorId(r.mentorId());
        st.setYearOfStudy(r.yearOfStudy());
        st.setStatus(r.status() == null ? StudentStatus.ACTIVE : r.status());
        return students.save(st);
    }

    // ---------------------------------------------------------------- helpers

    /** Open allocation per student in the current academic year. */
    public Map<Long, AdvisorAllocation> currentAllocations() {
        Optional<AcademicYear> y = years.findCurrent();
        Map<Long, AdvisorAllocation> m = new HashMap<>();
        y.ifPresent(year -> allocations.findByAcademicYear(year.getId()).stream()
                .filter(AdvisorAllocation::isOpen)
                .forEach(a -> m.put(a.getStudentId(), a)));
        return m;
    }

    public StudentSummary toSummary(Student st, Lookups.Snapshot s, AdvisorAllocation cur) {
        return new StudentSummary(st.getId(), st.getMatricNo(), st.getFirstName(), st.getLastName(), st.fullName(),
                st.getUniEmail(), st.getProgrammeId(), s.programmeName(st.getProgrammeId()), st.getEmployerId(),
                s.employerName(st.getEmployerId()), st.getMentorId(), s.mentorName(st.getMentorId()),
                st.getYearOfStudy(), st.getStatus(), cur == null ? null : cur.getAosUserId(),
                cur == null ? null : s.userName(cur.getAosUserId()));
    }

    public AllocationDto toDto(AdvisorAllocation a, Lookups.Snapshot s) {
        return new AllocationDto(a.getId(), a.getStudentId(), a.getAosUserId(), s.userName(a.getAosUserId()),
                s.userEmail(a.getAosUserId()), a.getAcademicYearId(), s.yearLabel(a.getAcademicYearId()),
                a.getValidFrom(), a.getValidTo(), a.getSource());
    }

    public EmployerDto toDto(Employer e) {
        return new EmployerDto(e.getId(), e.getName(), e.getAddress(), e.isActive());
    }

    public MentorDto toDto(WorkplaceMentor m, Lookups.Snapshot s) {
        return new MentorDto(m.getId(), m.getEmployerId(), s.employerName(m.getEmployerId()), m.getFullName(),
                m.getEmail(), m.getPhone(), m.isActive());
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim().toLowerCase(Locale.ROOT);
    }
}
