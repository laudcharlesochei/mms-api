package uk.ac.dundee.ga.mms.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AcademicYear;
import uk.ac.dundee.ga.mms.domain.AdvisorAllocation;
import uk.ac.dundee.ga.mms.domain.MeetingPeriod;
import uk.ac.dundee.ga.mms.domain.MeetingRevision;
import uk.ac.dundee.ga.mms.domain.MeetingStatus;
import uk.ac.dundee.ga.mms.domain.MentorMeeting;
import uk.ac.dundee.ga.mms.domain.Role;
import uk.ac.dundee.ga.mms.domain.Student;
import uk.ac.dundee.ga.mms.domain.WorkplaceMentor;
import uk.ac.dundee.ga.mms.storage.AcademicYearStore;
import uk.ac.dundee.ga.mms.storage.AllocationStore;
import uk.ac.dundee.ga.mms.storage.MeetingStore;
import uk.ac.dundee.ga.mms.storage.MentorStore;
import uk.ac.dundee.ga.mms.storage.RevisionStore;
import uk.ac.dundee.ga.mms.storage.StudentStore;
import uk.ac.dundee.ga.mms.util.TextSanitizer;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingDto;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingRequest;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingSummary;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.RevisionDto;
import uk.ac.dundee.ga.mms.web.error.ApiException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Mentor meeting records (FR-10 to FR-18, FR-26): create, draft, submit, amend within the edit
 * window with revisions, soft delete and restore. Only the student's Advisor of Studies writes.
 */
@Service
public class MeetingService {

    private final MeetingStore meetings;
    private final RevisionStore revisions;
    private final StudentStore students;
    private final MentorStore mentors;
    private final AllocationStore allocations;
    private final AcademicYearStore years;
    private final ScopeService scope;
    private final Lookups lookups;
    private final AuditService audit;
    private final MailService mail;
    private final MmsProperties props;
    private final ObjectMapper json;
    private final Clock clock;

    public MeetingService(MeetingStore meetings, RevisionStore revisions, StudentStore students, MentorStore mentors,
                          AllocationStore allocations, AcademicYearStore years, ScopeService scope, Lookups lookups,
                          AuditService audit, MailService mail, MmsProperties props, ObjectMapper json, Clock clock) {
        this.meetings = meetings;
        this.revisions = revisions;
        this.students = students;
        this.mentors = mentors;
        this.allocations = allocations;
        this.years = years;
        this.scope = scope;
        this.lookups = lookups;
        this.audit = audit;
        this.mail = mail;
        this.props = props;
        this.json = json;
        this.clock = clock;
    }

    private LocalDate today() {
        return LocalDate.now(clock.withZone(DashboardService.UK));
    }

    // ------------------------------------------------------------------ create / update / submit

    @Transactional
    public MeetingDto create(CurrentUser u, MeetingRequest r) {
        if (!u.canAdvise()) {
            throw ApiException.forbidden("Only an Advisor of Studies can record meetings.");
        }
        Optional<MentorMeeting> existing = meetings.findByClientRequestId(r.clientRequestId());
        if (existing.isPresent()) { // idempotent retry
            if (!existing.get().getAosUserId().equals(u.id())) {
                throw ApiException.conflict("duplicate-request", "Request ID already used");
            }
            return toDto(existing.get(), u);
        }
        Student st = students.findById(r.studentId()).orElseThrow(() -> ApiException.notFound("Student"));
        LocalDate date = r.meetingDate() == null ? today() : r.meetingDate();
        AcademicYear year = yearFor(date);
        requireAdvisor(u, st.getId(), year, date);

        MentorMeeting m = new MentorMeeting();
        m.setStudentId(st.getId());
        m.setAosUserId(u.id());
        m.setMentorId(st.getMentorId());
        m.setAcademicYearId(year.getId());
        m.setCreatedBy(u.id());
        m.setCreatedAt(Instant.now(clock));
        m.setClientRequestId(r.clientRequestId());
        apply(m, r, st);
        m.setMeetingDate(date);
        boolean submit = r.status() == MeetingStatus.SUBMITTED;
        validate(m, year, submit);
        if (submit) {
            checkDuplicate(m, r.confirmDuplicate());
            m.setStatus(MeetingStatus.SUBMITTED);
            m.setSubmittedAt(Instant.now(clock));
        } else {
            m.setStatus(MeetingStatus.DRAFT);
        }
        assignPeriod(m);
        m.setUpdatedBy(u.id());
        m.setUpdatedAt(Instant.now(clock));
        MentorMeeting saved = meetings.save(m);
        audit.record(submit ? "MEETING_SUBMIT" : "MEETING_CREATE", "MentorMeeting", saved.getId(),
                Map.of("studentId", st.getId(), "status", saved.getStatus().name()));
        if (submit) {
            notifySubmitted(saved.getId());
        }
        return toDto(saved, u);
    }

    @Transactional
    public MeetingDto update(CurrentUser u, Long id, MeetingRequest r, Integer ifMatch) {
        MentorMeeting m = requireOwnEditable(u, id, ifMatch);
        if (!m.getStudentId().equals(r.studentId())) {
            throw ApiException.badRequest("The student of a meeting cannot be changed.");
        }
        Student st = students.findById(m.getStudentId()).orElseThrow();
        boolean wasSubmitted = m.isSubmitted();
        // snapshot before the change; written only after validation passes (no orphan revisions)
        String before = wasSubmitted ? snapshot(m, u) : null;
        Integer beforeVersion = m.getVersion();
        apply(m, r, st);
        if (r.meetingDate() != null) {
            m.setMeetingDate(r.meetingDate());
            AcademicYear y = yearFor(r.meetingDate());
            m.setAcademicYearId(y.getId());
        }
        AcademicYear year = years.findById(m.getAcademicYearId()).orElseThrow();
        boolean submitNow = !wasSubmitted && r.status() == MeetingStatus.SUBMITTED;
        validate(m, year, wasSubmitted || submitNow);
        if (submitNow) {
            checkDuplicate(m, r.confirmDuplicate());
            m.setStatus(MeetingStatus.SUBMITTED);
            m.setSubmittedAt(Instant.now(clock));
        }
        assignPeriod(m);
        m.setUpdatedBy(u.id());
        m.setUpdatedAt(Instant.now(clock));
        if (wasSubmitted) {
            writeRevision(m.getId(), beforeVersion, before, u);
        }
        MentorMeeting saved = meetings.save(m);
        String action = wasSubmitted ? "MEETING_AMEND" : submitNow ? "MEETING_SUBMIT" : "MEETING_EDIT";
        audit.record(action, "MentorMeeting", id, Map.of("version", String.valueOf(saved.getVersion())));
        if (submitNow) {
            notifySubmitted(saved.getId());
        }
        return toDto(saved, u);
    }

    @Transactional
    public MeetingDto submit(CurrentUser u, Long id, Integer ifMatch, boolean confirmDuplicate) {
        MentorMeeting m = requireOwnEditable(u, id, ifMatch);
        if (m.isSubmitted()) {
            return toDto(m, u);
        }
        AcademicYear year = years.findById(m.getAcademicYearId()).orElseThrow();
        validate(m, year, true);
        checkDuplicate(m, confirmDuplicate);
        m.setStatus(MeetingStatus.SUBMITTED);
        m.setSubmittedAt(Instant.now(clock));
        m.setUpdatedBy(u.id());
        m.setUpdatedAt(Instant.now(clock));
        assignPeriod(m);
        MentorMeeting saved = meetings.save(m);
        audit.record("MEETING_SUBMIT", "MentorMeeting", id, Map.of());
        notifySubmitted(saved.getId());
        return toDto(saved, u);
    }

    // ------------------------------------------------------------------ delete / restore

    @Transactional
    public void delete(CurrentUser u, Long id, String reason) {
        MentorMeeting m = meetings.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Meeting"));
        String why = TextSanitizer.clean(reason);
        if (m.getStatus() == MeetingStatus.DRAFT && m.getAosUserId().equals(u.id())) {
            why = why == null ? "Draft discarded by author" : why;
        } else {
            if (!u.seesAll()) {
                throw ApiException.forbidden("Only the Programme Director or an Administrator can delete a submitted record.");
            }
            if (why == null || why.length() < 3) {
                throw ApiException.validation(Map.of("reason", "A reason is required to delete a record"));
            }
        }
        m.setDeletedAt(Instant.now(clock));
        m.setDeletedBy(u.id());
        m.setDeleteReason(why);
        meetings.save(m);
        audit.record("MEETING_DELETE", "MentorMeeting", id, Map.of("reason", why));
    }

    @Transactional
    public MeetingDto restore(CurrentUser u, Long id) {
        if (!u.has(Role.ADMIN)) {
            throw ApiException.forbidden("Only an Administrator can restore deleted records.");
        }
        MentorMeeting m = meetings.findById(id).filter(MentorMeeting::isDeleted).orElseThrow(() -> ApiException.notFound("Deleted meeting"));
        m.setDeletedAt(null);
        m.setDeletedBy(null);
        m.setDeleteReason(null);
        MentorMeeting saved = meetings.save(m);
        audit.record("MEETING_RESTORE", "MentorMeeting", id, Map.of());
        return toDto(saved, u);
    }

    // ------------------------------------------------------------------ reads

    public MeetingDto get(CurrentUser u, Long id) {
        MentorMeeting m = requireVisible(u, id);
        audit.record("MEETING_VIEW", "MentorMeeting", id, Map.of());
        return toDto(m, u);
    }

    public MentorMeeting requireVisible(CurrentUser u, Long id) {
        MentorMeeting m = meetings.findById(id).orElseThrow(() -> ApiException.notFound("Meeting"));
        if (m.isDeleted()) {
            if (!u.seesAll()) {
                throw ApiException.notFound("Meeting");
            }
            return m;
        }
        if (m.getStatus() == MeetingStatus.DRAFT && !m.getAosUserId().equals(u.id())) {
            throw ApiException.notFound("Meeting");
        }
        if (m.getAosUserId().equals(u.id())) {
            return m;
        }
        scope.requireVisibleStudent(u, m.getStudentId());
        return m;
    }

    /** All meetings for a student, newest first: submitted ones plus the caller's own drafts (FR-17). */
    public List<MeetingSummary> listForStudent(CurrentUser u, Long studentId) {
        scope.requireVisibleStudent(u, studentId);
        Lookups.Snapshot s = lookups.snapshot();
        String name = students.findById(studentId).map(Student::fullName).orElse(null);
        return meetings.findByStudent(studentId).stream()
                .filter(m -> m.isSubmitted() || m.getAosUserId().equals(u.id()))
                .sorted(Comparator.comparing(MentorMeeting::getMeetingDate).thenComparing(MentorMeeting::getId).reversed())
                .map(m -> summary(m, name, s)).toList();
    }

    /** Previous submitted meeting for the side panel (FR-14). */
    public Optional<MeetingDto> latestForStudent(CurrentUser u, Long studentId, Long excludeId) {
        scope.requireVisibleStudent(u, studentId);
        return meetings.findByStudent(studentId).stream()
                .filter(MentorMeeting::isSubmitted)
                .filter(m -> excludeId == null || !excludeId.equals(m.getId()))
                .max(Comparator.comparing(MentorMeeting::getMeetingDate).thenComparing(MentorMeeting::getId))
                .map(m -> toDto(m, u));
    }

    public List<MeetingSummary> drafts(CurrentUser u) {
        Lookups.Snapshot s = lookups.snapshot();
        return meetings.findDraftsByAuthor(u.id()).stream()
                .sorted(Comparator.comparing(MentorMeeting::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .map(m -> summary(m, students.findById(m.getStudentId()).map(Student::fullName).orElse(null), s))
                .toList();
    }

    public List<MeetingSummary> deleted(CurrentUser u) {
        if (!u.seesAll()) {
            throw ApiException.forbidden("Only the Programme Director or an Administrator can view deleted records.");
        }
        Lookups.Snapshot s = lookups.snapshot();
        return meetings.findDeleted().stream()
                .sorted(Comparator.comparing(MentorMeeting::getDeletedAt).reversed())
                .map(m -> summary(m, students.findById(m.getStudentId()).map(Student::fullName).orElse(null), s))
                .toList();
    }

    public List<RevisionDto> revisions(CurrentUser u, Long id) {
        requireVisible(u, id);
        Lookups.Snapshot s = lookups.snapshot();
        return revisions.findByMeeting(id).stream()
                .map(r -> new RevisionDto(r.getId(), r.getVersion(), r.getChangedAt(), r.getChangedBy(),
                        s.userName(r.getChangedBy()), r.getSnapshotJson()))
                .toList();
    }

    // ------------------------------------------------------------------ rules

    private MentorMeeting requireOwnEditable(CurrentUser u, Long id, Integer ifMatch) {
        MentorMeeting m = meetings.findById(id).filter(x -> !x.isDeleted()).orElseThrow(() -> ApiException.notFound("Meeting"));
        if (!m.getAosUserId().equals(u.id())) {
            throw ApiException.forbidden("Only the author can edit this record.");
        }
        if (ifMatch != null && !ifMatch.equals(m.getVersion())) {
            throw ApiException.preconditionFailed("This record was changed since you opened it. Reload and try again.");
        }
        if (m.isSubmitted() && !withinEditWindow(m)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "edit-window-closed",
                    "The " + props.getEditWindowDays() + "-day edit window for this record has closed.");
        }
        return m;
    }

    private boolean withinEditWindow(MentorMeeting m) {
        Instant until = editableUntil(m);
        return until == null || Instant.now(clock).isBefore(until);
    }

    private Instant editableUntil(MentorMeeting m) {
        if (!m.isSubmitted() || m.getSubmittedAt() == null) {
            return null;
        }
        return m.getSubmittedAt().plus(Duration.ofDays(props.getEditWindowDays()));
    }

    private void requireAdvisor(CurrentUser u, Long studentId, AcademicYear year, LocalDate date) {
        boolean ok = allocations.findByStudent(studentId).stream()
                .filter(a -> a.getAcademicYearId().equals(year.getId()) && u.id().equals(a.getAosUserId()))
                .anyMatch(a -> a.isOpen() || covers(a, date));
        if (!ok) {
            audit.record("ACCESS_DENIED", "Student", studentId, Map.of("reason", "not the student's advisor"));
            throw ApiException.forbidden("You are not this student's Advisor of Studies for " + year.getLabel() + ".");
        }
    }

    private static boolean covers(AdvisorAllocation a, LocalDate d) {
        return !d.isBefore(a.getValidFrom()) && (a.getValidTo() == null || !d.isAfter(a.getValidTo()));
    }

    private AcademicYear yearFor(LocalDate date) {
        return years.findContaining(date).or(years::findCurrent)
                .orElseThrow(() -> ApiException.badRequest("No academic year covers " + date + "."));
    }

    private void apply(MentorMeeting m, MeetingRequest r, Student st) {
        if (r.format() != null) {
            m.setFormat(r.format());
        }
        m.setStudentPresent(Boolean.TRUE.equals(r.studentPresent()));
        m.setMentorPresent(Boolean.TRUE.equals(r.mentorPresent()));
        if (r.mentorId() != null) {
            WorkplaceMentor mentor = mentors.findById(r.mentorId()).orElseThrow(() -> ApiException.badRequest("Mentor not found"));
            if (st.getEmployerId() != null && !st.getEmployerId().equals(mentor.getEmployerId())) {
                throw ApiException.validation(Map.of("mentorId", "Choose a mentor from the student's employer"));
            }
            m.setMentorId(mentor.getId());
        }
        m.setWorkActivities(TextSanitizer.clean(r.workActivities()));
        m.setUniversityProgress(TextSanitizer.clean(r.universityProgress()));
        m.setUniversityFeedback(TextSanitizer.clean(r.universityFeedback()));
        m.setTrainingOpportunities(TextSanitizer.clean(r.trainingOpportunities()));
        m.setAgreedActions(TextSanitizer.clean(r.agreedActions()));
        m.setConcernFlag(Boolean.TRUE.equals(r.concernFlag()));
        m.setConcernNote(m.isConcernFlag() ? TextSanitizer.clean(r.concernNote()) : null);
        m.setNextMeetingDate(r.nextMeetingDate());
    }

    /** FR-12/13/16 and Section 8.2. Drafts get the light checks; submit gets all of them. */
    void validate(MentorMeeting m, AcademicYear year, boolean submit) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDate d = m.getMeetingDate();
        if (d == null) {
            errors.put("meetingDate", "Meeting date is required");
        } else {
            if (d.isAfter(today())) {
                errors.put("meetingDate", "Meeting date cannot be in the future");
            }
            if (d.isBefore(year.getStartDate())) {
                errors.put("meetingDate", "Meeting date cannot be before the academic year start (" + year.getStartDate() + ")");
            }
        }
        if (m.getNextMeetingDate() != null && d != null && !m.getNextMeetingDate().isAfter(d)) {
            errors.put("nextMeetingDate", "Proposed next meeting must be after the meeting date");
        }
        checkLength(errors, "workActivities", m.getWorkActivities(), MeetingDtos.NOTES_MAX);
        checkLength(errors, "universityProgress", m.getUniversityProgress(), MeetingDtos.NOTES_MAX);
        checkLength(errors, "universityFeedback", m.getUniversityFeedback(), MeetingDtos.NOTES_MAX);
        checkLength(errors, "trainingOpportunities", m.getTrainingOpportunities(), MeetingDtos.NOTES_MAX);
        checkLength(errors, "agreedActions", m.getAgreedActions(), MeetingDtos.NOTES_MAX);
        checkLength(errors, "concernNote", m.getConcernNote(), MeetingDtos.CONCERN_MAX);
        if (submit) {
            if (m.getFormat() == null) {
                errors.put("format", "Choose in person, online or phone");
            }
            if (!m.isStudentPresent() && !m.isMentorPresent()) {
                errors.put("present", "Tick at least one attendee");
            }
            if (!TextSanitizer.hasText(m.getWorkActivities()) && !TextSanitizer.hasText(m.getUniversityProgress())
                    && !TextSanitizer.hasText(m.getUniversityFeedback()) && !TextSanitizer.hasText(m.getTrainingOpportunities())
                    && !TextSanitizer.hasText(m.getAgreedActions())) {
                errors.put("notes", "Write something in at least one notes field");
            }
            if (m.isConcernFlag() && !TextSanitizer.hasText(m.getConcernNote())) {
                errors.put("concernNote", "Add a note explaining the engagement concern");
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
    }

    private static void checkLength(Map<String, String> errors, String field, String value, int max) {
        if (value != null && value.length() > max) {
            errors.put(field, "At most " + max + " characters");
        }
    }

    private void checkDuplicate(MentorMeeting m, Boolean confirm) {
        if (Boolean.TRUE.equals(confirm)) {
            return;
        }
        List<MentorMeeting> same = meetings.findSubmittedByStudentAndDate(m.getStudentId(), m.getMeetingDate()).stream()
                .filter(x -> !x.getId().equals(m.getId())).toList();
        if (!same.isEmpty()) {
            ApiException e = new ApiException(HttpStatus.CONFLICT, "duplicate-meeting",
                    "A submitted meeting for this student already exists on " + m.getMeetingDate()
                            + ". Confirm to record another one.",
                    Map.of("existingMeetingId", String.valueOf(same.get(0).getId())));
            throw e;
        }
    }

    private void assignPeriod(MentorMeeting m) {
        m.setPeriodId(years.findPeriods(m.getAcademicYearId()).stream()
                .filter(p -> p.contains(m.getMeetingDate())).map(MeetingPeriod::getId).findFirst().orElse(null));
    }

    private String snapshot(MentorMeeting m, CurrentUser u) {
        try {
            return json.writeValueAsString(toDto(m, u));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    private void writeRevision(Long meetingId, Integer version, String snapshotJson, CurrentUser u) {
        MeetingRevision rev = new MeetingRevision();
        rev.setMeetingId(meetingId);
        rev.setVersion(version == null ? 0 : version);
        rev.setChangedBy(u.id());
        rev.setChangedAt(Instant.now(clock));
        rev.setSnapshotJson(snapshotJson);
        revisions.save(rev);
    }

    private void notifySubmitted(Long meetingId) {
        Runnable r = () -> mail.meetingSubmitted(meetingId);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    r.run();
                }
            });
        } else {
            r.run();
        }
    }

    // ------------------------------------------------------------------ mapping

    public MeetingDto toDto(MentorMeeting m, CurrentUser u) {
        Lookups.Snapshot s = lookups.snapshot();
        Student st = students.findById(m.getStudentId()).orElse(null);
        Integer periodSeq = years.findPeriod(m.getPeriodId()).map(MeetingPeriod::getSeq).orElse(null);
        boolean author = u != null && m.getAosUserId().equals(u.id());
        boolean canEdit = author && !m.isDeleted() && (!m.isSubmitted() || withinEditWindow(m));
        boolean canDelete = !m.isDeleted() && ((author && !m.isSubmitted()) || (u != null && u.seesAll()));
        return new MeetingDto(m.getId(), m.getStudentId(), st == null ? null : st.fullName(),
                st == null ? null : st.getMatricNo(), st == null ? null : s.programmeName(st.getProgrammeId()),
                st == null ? null : s.employerName(st.getEmployerId()), m.getAosUserId(), s.userName(m.getAosUserId()),
                s.userEmail(m.getAosUserId()), m.getMentorId(), s.mentorName(m.getMentorId()), m.getAcademicYearId(),
                s.yearLabel(m.getAcademicYearId()), periodSeq, m.getMeetingDate(), m.getFormat(), m.isStudentPresent(),
                m.isMentorPresent(), m.getWorkActivities(), m.getUniversityProgress(), m.getUniversityFeedback(),
                m.getTrainingOpportunities(), m.getAgreedActions(), m.isConcernFlag(), m.getConcernNote(),
                m.getNextMeetingDate(), m.getStatus(), m.getSubmittedAt(), m.getVersion(), m.getCreatedAt(),
                m.getUpdatedAt(), m.isDeleted(), m.getDeletedAt(), m.getDeleteReason(), canEdit, canDelete,
                editableUntil(m));
    }

    private MeetingSummary summary(MentorMeeting m, String studentName, Lookups.Snapshot s) {
        return new MeetingSummary(m.getId(), m.getStudentId(), studentName, m.getMeetingDate(), s.userName(m.getAosUserId()),
                m.getFormat(), m.isConcernFlag(), m.getStatus(), m.getSubmittedAt(), m.getDeletedAt(), m.getDeleteReason());
    }
}
