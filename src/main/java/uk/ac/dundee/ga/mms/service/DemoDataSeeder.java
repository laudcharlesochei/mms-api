package uk.ac.dundee.ga.mms.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.AllocationSource;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Employer;
import uk.ac.dundee.ga.mms.domain.MeetingFormat;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.domain.MeetingStatus;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.Programme;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.domain.StudentStatus;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.EmployerStore;
import uk.ac.dundee.ga.mms.storage.MeetingStore;
import uk.ac.dundee.ga.mms.storage.MentorStore;
import uk.ac.dundee.ga.mms.storage.ProgrammeStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.storage.UserStore;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Random;

/**
 * Synthetic demo data for local development and demos (SEED_DEMO_DATA=true). Never real student
 * data (Section 11.3). Runs only when no students exist. All demo passwords are "Password123!".
 */
@Slf4j
@Component
@Order(2)
public class DemoDataSeeder implements ApplicationRunner {

    public static final String DEMO_PASSWORD = "Password123!";

    private static final String[] FIRST = {"Aisha", "Ben", "Chloe", "Daniel", "Ella", "Finlay", "Grace", "Harris",
            "Isla", "Jack", "Kirsty", "Lewis", "Morag", "Nathan", "Olivia", "Patrick", "Rhona", "Sami", "Tara", "Umar",
            "Victoria", "Wei", "Yasmin", "Zak"};
    private static final String[] LAST = {"Anderson", "Brown", "Campbell", "Duncan", "Ewing", "Fraser", "Gordon",
            "Hamilton", "Irvine", "Johnston", "Kerr", "Lindsay", "MacLeod", "Murray", "Nicol", "Okafor", "Paterson",
            "Ross", "Stewart", "Thomson", "Wallace", "Young"};
    private static final String[] EMPLOYERS = {"Tay Digital Ltd", "Discovery Data Services", "Caledonian Cyber",
            "Dundee Health Tech", "Angus Logistics Group", "Northern Bank Systems"};
    private static final String[] ACTIVITIES = {"Moved to the data platform team; building ETL jobs.",
            "Working on the customer portal front end in React.", "Supporting the security operations centre on triage.",
            "Leading a small automation project for invoice processing.", "Shadowing the architecture review board."};
    private static final String[] PROGRESS = {"On track with modules; worried about exam timing.",
            "Enjoying the project module; needs to start the literature review.", "Good progress, coursework submitted on time.",
            "Finding the maths module difficult; will attend extra tutorials."};
    private static final String[] ACTIONS = {"Student to share project plan with mentor by December.",
            "Mentor to arrange time for off-the-job learning each Friday.", "AoS to check in after the January exams.",
            "Student to book a meeting with the module leader."};

    private final MmsProperties props;
    private final UserStore users;
    private final ProgrammeStore programmes;
    private final EmployerStore employers;
    private final MentorStore mentors;
    private final StudentStore students;
    private final AcademicYearStore years;
    private final AllocationStore allocations;
    private final MeetingStore meetings;
    private final PasswordEncoder encoder;
    private final Random rnd = new Random(42);

    public DemoDataSeeder(MmsProperties props, UserStore users, ProgrammeStore programmes, EmployerStore employers,
                          MentorStore mentors, StudentStore students, AcademicYearStore years,
                          AllocationStore allocations, MeetingStore meetings, PasswordEncoder encoder) {
        this.props = props;
        this.users = users;
        this.programmes = programmes;
        this.employers = employers;
        this.mentors = mentors;
        this.students = students;
        this.years = years;
        this.allocations = allocations;
        this.meetings = meetings;
        this.encoder = encoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!props.isSeedDemoData() || props.isMigrateOnly() || !students.findAll().isEmpty()) {
            return;
        }
        log.info("Seeding synthetic demo data ...");
        String hash = encoder.encode(DEMO_PASSWORD);
        AppUser director = user("director@mms.local", "Dr Fiona Director", hash, Role.DIRECTOR, Role.AOS);
        AppUser leadSe = user("lead.se@mms.local", "Dr Chi Lead", hash, Role.PROG_LEAD, Role.AOS);
        AppUser leadDs = user("lead.ds@mms.local", "Dr Tunji Lead", hash, Role.PROG_LEAD, Role.AOS);
        List<AppUser> aos = new ArrayList<>(List.of(
                user("aos1@mms.local", "Dr Alan Advisor", hash, Role.AOS),
                user("aos2@mms.local", "Dr Beth Advisor", hash, Role.AOS),
                user("aos3@mms.local", "Dr Callum Advisor", hash, Role.AOS),
                leadSe, leadDs));
        aos.add(director);

        List<Programme> progs = programmes.findAll();
        progs.stream().filter(p -> "GA-SE".equals(p.getCode())).findFirst().ifPresent(p -> {
            p.setLeadUserId(leadSe.getId());
            programmes.save(p);
        });
        progs.stream().filter(p -> "GA-DS".equals(p.getCode())).findFirst().ifPresent(p -> {
            p.setLeadUserId(leadDs.getId());
            programmes.save(p);
        });

        List<Employer> emps = new ArrayList<>();
        List<WorkplaceMentor> ments = new ArrayList<>();
        for (String name : EMPLOYERS) {
            Employer e = new Employer();
            e.setName(name);
            e.setAddress("Dundee");
            e = employers.save(e);
            emps.add(e);
            for (int i = 0; i < 2; i++) {
                WorkplaceMentor m = new WorkplaceMentor();
                m.setEmployerId(e.getId());
                m.setFullName(FIRST[rnd.nextInt(FIRST.length)] + " " + LAST[rnd.nextInt(LAST.length)]);
                m.setEmail(m.getFullName().toLowerCase().replace(' ', '.') + "@example.com");
                ments.add(mentors.save(m));
            }
        }

        AcademicYear current = years.findCurrent().orElseThrow();
        AcademicYear previous = previousYear(current);
        List<MeetingPeriod> curPeriods = years.findPeriods(current.getId());
        List<MeetingPeriod> prevPeriods = years.findPeriods(previous.getId());
        LocalDate today = LocalDate.now(DashboardService.UK);

        for (int i = 0; i < 40; i++) {
            Programme prog = progs.get(i % progs.size());
            Employer emp = emps.get(rnd.nextInt(emps.size()));
            List<WorkplaceMentor> empMentors = ments.stream().filter(m -> m.getEmployerId().equals(emp.getId())).toList();
            Student s = new Student();
            s.setMatricNo(String.valueOf(2400100 + i));
            s.setFirstName(FIRST[(i * 7) % FIRST.length]);
            s.setLastName(LAST[(i * 5) % LAST.length]);
            s.setUniEmail(s.getMatricNo() + "@dundee.ac.uk");
            s.setProgrammeId(prog.getId());
            s.setEmployerId(emp.getId());
            s.setMentorId(empMentors.get(0).getId());
            s.setYearOfStudy(1 + i % 4);
            s.setStatus(StudentStatus.ACTIVE);
            if (i == 0) {
                s.setUniEmail("student@mms.local");
            }
            s = students.save(s);

            AppUser adv = aos.get(i % aos.size());
            AppUser prevAdv = aos.get((i + 1) % aos.size());
            allocation(s, prevAdv, previous, previous.getStartDate(), previous.getEndDate());
            allocation(s, adv, current, current.getStartDate(), null);

            // previous year: mostly complete, some gaps (shows Overdue in the 2025/26 view)
            for (MeetingPeriod p : prevPeriods) {
                if (rnd.nextInt(10) < 8) {
                    meeting(s, prevAdv, previous, p, randomDate(p.getStartDate(), p.getEndDate()), false);
                }
            }
            // current year: some meetings in period 1 so far
            for (MeetingPeriod p : curPeriods) {
                if (p.getStartDate().isAfter(today)) {
                    continue;
                }
                LocalDate end = p.getEndDate().isAfter(today) ? today : p.getEndDate();
                if (rnd.nextInt(10) < 5) {
                    meeting(s, adv, current, p, randomDate(p.getStartDate(), end), rnd.nextInt(10) == 0);
                }
            }
        }
        user("student@mms.local", "Aisha Anderson (student)", hash, Role.STUDENT);
        user("admin.ops@mms.local", "Ops Administrator", hash, Role.ADMIN);
        log.info("Demo data ready. Sign in as director@mms.local, lead.se@mms.local, aos1@mms.local or student@mms.local with password {}", DEMO_PASSWORD);
    }

    private AcademicYear previousYear(AcademicYear current) {
        LocalDate start = current.getStartDate().minusYears(1);
        LocalDate end = current.getEndDate().minusYears(1);
        String label = start.getYear() + "/" + String.valueOf(end.getYear()).substring(2);
        return years.findByLabel(label).orElseGet(() -> {
            AcademicYear y = new AcademicYear();
            y.setLabel(label);
            y.setStartDate(start);
            y.setEndDate(end);
            y.setRequiredMeetings(current.getRequiredMeetings());
            y.setCurrent(false);
            AcademicYear saved = years.save(y);
            for (MeetingPeriod p : AcademicYearService.split(saved.getId(), start, end, saved.getRequiredMeetings())) {
                years.savePeriod(p);
            }
            return saved;
        });
    }

    private AppUser user(String email, String name, String hash, Role... roles) {
        return users.findByEmail(email).orElseGet(() -> {
            AppUser u = new AppUser();
            u.setEmail(email);
            u.setDisplayName(name);
            u.setPasswordHash(hash);
            u.setRoles(EnumSet.of(roles[0], roles));
            u.setCreatedAt(Instant.now());
            return users.save(u);
        });
    }

    private void allocation(Student s, AppUser aos, AcademicYear y, LocalDate from, LocalDate to) {
        AdvisorAllocation a = new AdvisorAllocation();
        a.setStudentId(s.getId());
        a.setAosUserId(aos.getId());
        a.setAcademicYearId(y.getId());
        a.setValidFrom(from);
        a.setValidTo(to);
        a.setSource(AllocationSource.IMPORT);
        allocations.save(a);
    }

    private void meeting(Student s, AppUser aos, AcademicYear y, MeetingPeriod p, LocalDate date, boolean concern) {
        MentorMeeting m = new MentorMeeting();
        m.setStudentId(s.getId());
        m.setAosUserId(aos.getId());
        m.setMentorId(s.getMentorId());
        m.setAcademicYearId(y.getId());
        m.setPeriodId(p.getId());
        m.setMeetingDate(date);
        m.setFormat(MeetingFormat.values()[rnd.nextInt(3)]);
        m.setStudentPresent(true);
        m.setMentorPresent(rnd.nextBoolean());
        m.setWorkActivities(ACTIVITIES[rnd.nextInt(ACTIVITIES.length)]);
        m.setUniversityProgress(PROGRESS[rnd.nextInt(PROGRESS.length)]);
        m.setUniversityFeedback(rnd.nextBoolean() ? "Would like lecture recordings released earlier." : null);
        m.setTrainingOpportunities(rnd.nextBoolean() ? "Employer funding a cloud certification." : null);
        m.setAgreedActions(ACTIONS[rnd.nextInt(ACTIONS.length)]);
        m.setConcernFlag(concern);
        m.setConcernNote(concern ? "Student has missed two workplace check-ins; follow up with mentor." : null);
        m.setStatus(MeetingStatus.SUBMITTED);
        Instant at = date.atTime(16, 0).toInstant(ZoneOffset.UTC);
        m.setSubmittedAt(at);
        m.setCreatedAt(at);
        m.setUpdatedAt(at);
        m.setCreatedBy(aos.getId());
        m.setUpdatedBy(aos.getId());
        meetings.save(m);
    }

    private LocalDate randomDate(LocalDate from, LocalDate to) {
        long span = to.toEpochDay() - from.toEpochDay();
        return from.plusDays(span <= 0 ? 0 : rnd.nextInt((int) span + 1));
    }
}
