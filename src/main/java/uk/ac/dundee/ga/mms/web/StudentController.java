package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.domain.StudentStatus;
import uk.ac.dundee.ga.mms.service.AllocationService;
import uk.ac.dundee.ga.mms.service.Lookups;
import uk.ac.dundee.ga.mms.service.MeetingService;
import uk.ac.dundee.ga.mms.service.ReferenceDataService;
import uk.ac.dundee.ga.mms.service.SubjectAccessService;
import uk.ac.dundee.ga.mms.storage.PageResult;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingDto;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingSummary;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.AllocationDto;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.ReassignRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.StudentDetail;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.StudentRequest;
import uk.ac.dundee.ga.mms.web.dto.ReferenceDtos.StudentSummary;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/students")
@Tag(name = "Students", description = "Student records, allocations and their meetings")
public class StudentController {

    private final ReferenceDataService reference;
    private final MeetingService meetings;
    private final AllocationService allocations;
    private final SubjectAccessService subjectAccess;
    private final CurrentUserService current;
    private final Lookups lookups;

    public StudentController(ReferenceDataService reference, MeetingService meetings, AllocationService allocations,
                             SubjectAccessService subjectAccess, CurrentUserService current, Lookups lookups) {
        this.reference = reference;
        this.meetings = meetings;
        this.allocations = allocations;
        this.subjectAccess = subjectAccess;
        this.current = current;
        this.lookups = lookups;
    }

    @GetMapping
    public PageResult<StudentSummary> search(@RequestParam(required = false) Long programme,
                                             @RequestParam(required = false) Long aos,
                                             @RequestParam(required = false) StudentStatus status,
                                             @RequestParam(required = false) String q,
                                             @RequestParam(defaultValue = "0") int page,
                                             @RequestParam(defaultValue = "50") int size) {
        return reference.searchStudents(current.get(), programme, aos, status, q, page, size);
    }

    @GetMapping("/{id}")
    public StudentDetail get(@PathVariable Long id) {
        return reference.studentDetail(current.get(), id);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public StudentSummary create(@Valid @RequestBody StudentRequest r) {
        CurrentUser u = current.get();
        var st = reference.saveStudent(u, null, r);
        return reference.toSummary(st, lookups.snapshot(), null);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public StudentSummary update(@PathVariable Long id, @Valid @RequestBody StudentRequest r) {
        CurrentUser u = current.get();
        var st = reference.saveStudent(u, id, r);
        return reference.toSummary(st, lookups.snapshot(), reference.currentAllocations().get(id));
    }

    @GetMapping("/{id}/meetings")
    public List<MeetingSummary> meetings(@PathVariable Long id) {
        return meetings.listForStudent(current.get(), id);
    }

    /** Previous submitted meeting, shown read-only beside the new form (FR-14). 204 when none. */
    @GetMapping("/{id}/meetings/latest")
    public ResponseEntity<MeetingDto> latest(@PathVariable Long id, @RequestParam(required = false) Long exclude) {
        return meetings.latestForStudent(current.get(), id, exclude)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    /** Reassign the student's Advisor of Studies (history kept) - FR-09. */
    @PostMapping("/{id}/allocations")
    @PreAuthorize("hasAnyRole('ADMIN','DIRECTOR','PROG_LEAD')")
    public AllocationDto reassign(@PathVariable Long id, @Valid @RequestBody ReassignRequest r) {
        return allocations.reassign(current.get(), id, r);
    }

    /** Subject access request: everything held about the student (Admin). */
    @GetMapping("/{id}/subject-access")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> subjectAccess(@PathVariable Long id) {
        return subjectAccess.export(current.get(), id);
    }

    /** Erasure on a valid request (Admin). Permanently removes the student and their meetings. */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, Object> erase(@PathVariable Long id) {
        return subjectAccess.erase(current.get(), id);
    }
}
