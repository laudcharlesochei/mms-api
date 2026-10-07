package uk.ac.dundee.ga.mms.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.MeetingStore;
import uk.ac.dundee.ga.mms.storage.RevisionStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Data subject rights (Section 9.2): export everything held about a student, and erase on a valid request. */
@Service
public class SubjectAccessService {

    private final StudentStore students;
    private final MeetingStore meetings;
    private final RevisionStore revisions;
    private final AllocationStore allocations;
    private final MeetingService meetingService;
    private final ReferenceDataService reference;
    private final AuditService audit;

    public SubjectAccessService(StudentStore students, MeetingStore meetings, RevisionStore revisions,
                                AllocationStore allocations, MeetingService meetingService,
                                ReferenceDataService reference, AuditService audit) {
        this.students = students;
        this.meetings = meetings;
        this.revisions = revisions;
        this.allocations = allocations;
        this.meetingService = meetingService;
        this.reference = reference;
        this.audit = audit;
    }

    public Map<String, Object> export(CurrentUser u, Long studentId) {
        requireAdmin(u);
        Student s = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generatedAt", Instant.now());
        out.put("student", reference.studentDetail(u, studentId));
        out.put("meetings", meetings.findAllForStudentIncludingDeleted(s.getId()).stream()
                .map(m -> meetingService.toDto(m, u)).toList());
        audit.record("SUBJECT_ACCESS_EXPORT", "Student", studentId, Map.of());
        return out;
    }

    @Transactional
    public Map<String, Object> erase(CurrentUser u, Long studentId) {
        requireAdmin(u);
        Student s = students.findById(studentId).orElseThrow(() -> ApiException.notFound("Student"));
        int n = eraseMeetings(s.getId());
        allocations.findByStudent(s.getId()).forEach(a -> allocations.deleteById(a.getId()));
        students.deleteById(s.getId());
        audit.record("SUBJECT_ERASURE", "Student", studentId, Map.of("meetingsErased", n));
        return Map.of("studentId", studentId, "meetingsErased", n);
    }

    @Transactional
    public int eraseMeetings(Long studentId) {
        List<MentorMeeting> ms = meetings.findAllForStudentIncludingDeleted(studentId);
        for (MentorMeeting m : ms) {
            revisions.deleteByMeeting(m.getId());
            meetings.deleteById(m.getId());
        }
        return ms.size();
    }

    private static void requireAdmin(CurrentUser u) {
        if (!u.has(Role.ADMIN)) {
            throw ApiException.forbidden("Only an Administrator can do this.");
        }
    }
}
