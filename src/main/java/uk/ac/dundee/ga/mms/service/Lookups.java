package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.Employer;
import uk.ac.dundee.ga.mms.domain.Programme;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.storage.EmployerStore;
import uk.ac.dundee.ga.mms.storage.MentorStore;
import uk.ac.dundee.ga.mms.storage.ProgrammeStore;
import uk.ac.dundee.ga.mms.storage.UserStore;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Loads small reference tables into maps so lists can show names without N+1 lookups. */
@Service
public class Lookups {

    private final UserStore users;
    private final ProgrammeStore programmes;
    private final EmployerStore employers;
    private final MentorStore mentors;
    private final AcademicYearStore years;

    public Lookups(UserStore users, ProgrammeStore programmes, EmployerStore employers, MentorStore mentors,
                   AcademicYearStore years) {
        this.users = users;
        this.programmes = programmes;
        this.employers = employers;
        this.mentors = mentors;
        this.years = years;
    }

    public Snapshot snapshot() {
        return new Snapshot(
                users.findAll().stream().collect(Collectors.toMap(AppUser::getId, Function.identity())),
                programmes.findAll().stream().collect(Collectors.toMap(Programme::getId, Function.identity())),
                employers.findAll().stream().collect(Collectors.toMap(Employer::getId, Function.identity())),
                mentors.findAll().stream().collect(Collectors.toMap(WorkplaceMentor::getId, Function.identity())),
                years.findAll().stream().collect(Collectors.toMap(AcademicYear::getId, Function.identity())));
    }

    public record Snapshot(Map<Long, AppUser> users, Map<Long, Programme> programmes, Map<Long, Employer> employers,
                           Map<Long, WorkplaceMentor> mentors, Map<Long, AcademicYear> years) {

        public String userName(Long id) {
            AppUser u = id == null ? null : users.get(id);
            return u == null ? null : u.getDisplayName();
        }

        public String userEmail(Long id) {
            AppUser u = id == null ? null : users.get(id);
            return u == null ? null : u.getEmail();
        }

        public String programmeName(Long id) {
            Programme p = id == null ? null : programmes.get(id);
            return p == null ? null : p.getName();
        }

        public String programmeCode(Long id) {
            Programme p = id == null ? null : programmes.get(id);
            return p == null ? null : p.getCode();
        }

        public String employerName(Long id) {
            Employer e = id == null ? null : employers.get(id);
            return e == null ? null : e.getName();
        }

        public String mentorName(Long id) {
            WorkplaceMentor m = id == null ? null : mentors.get(id);
            return m == null ? null : m.getFullName();
        }

        public String yearLabel(Long id) {
            AcademicYear y = id == null ? null : years.get(id);
            return y == null ? null : y.getLabel();
        }
    }
}
