package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.auth.CurrentUser;
import uk.ac.dundee.ga.mms.auth.CurrentUserService;
import uk.ac.dundee.ga.mms.service.AuditService;
import uk.ac.dundee.ga.mms.service.MeetingService;
import uk.ac.dundee.ga.mms.service.PdfService;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.DeleteRequest;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingDto;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingRequest;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.MeetingSummary;
import uk.ac.dundee.ga.mms.web.dto.MeetingDtos.RevisionDto;

import java.net.URI;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/meetings")
@Tag(name = "Meetings", description = "Mentor meeting records (FR-10 to FR-18)")
public class MeetingController {

    private final MeetingService meetings;
    private final PdfService pdf;
    private final CurrentUserService current;
    private final AuditService audit;

    public MeetingController(MeetingService meetings, PdfService pdf, CurrentUserService current, AuditService audit) {
        this.meetings = meetings;
        this.pdf = pdf;
        this.current = current;
        this.audit = audit;
    }

    /** Create a draft or submit directly ({@code status} in the body). */
    @PostMapping
    public ResponseEntity<MeetingDto> create(@Valid @RequestBody MeetingRequest r) {
        MeetingDto dto = meetings.create(current.get(), r);
        return ResponseEntity.created(URI.create("/api/v1/meetings/" + dto.id()))
                .eTag(etag(dto.version())).body(dto);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MeetingDto> get(@PathVariable Long id) {
        MeetingDto dto = meetings.get(current.get(), id);
        return ResponseEntity.ok().eTag(etag(dto.version())).body(dto);
    }

    /** Update a draft or amend within the edit window. Send {@code If-Match: "<version>"}. */
    @PutMapping("/{id}")
    public ResponseEntity<MeetingDto> update(@PathVariable Long id, @Valid @RequestBody MeetingRequest r,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        MeetingDto dto = meetings.update(current.get(), id, r, parseIfMatch(ifMatch));
        return ResponseEntity.ok().eTag(etag(dto.version())).body(dto);
    }

    @PostMapping("/{id}/submit")
    public ResponseEntity<MeetingDto> submit(@PathVariable Long id,
                                             @RequestParam(defaultValue = "false") boolean confirmDuplicate,
                                             @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch) {
        MeetingDto dto = meetings.submit(current.get(), id, parseIfMatch(ifMatch), confirmDuplicate);
        return ResponseEntity.ok().eTag(etag(dto.version())).body(dto);
    }

    /** Soft delete with reason (Director/Admin for submitted records; author for own drafts). */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id, @RequestBody(required = false) DeleteRequest r,
                                       @RequestParam(required = false) String reason) {
        meetings.delete(current.get(), id, r != null && r.reason() != null ? r.reason() : reason);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    public MeetingDto restore(@PathVariable Long id) {
        return meetings.restore(current.get(), id);
    }

    @GetMapping("/{id}/revisions")
    public List<RevisionDto> revisions(@PathVariable Long id) {
        return meetings.revisions(current.get(), id);
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@PathVariable Long id) {
        CurrentUser u = current.get();
        MeetingDto dto = meetings.toDto(meetings.requireVisible(u, id), u);
        byte[] bytes = pdf.render(dto);
        audit.record("MEETING_PDF", "MentorMeeting", id, Map.of());
        String name = "mentor-meeting-" + (dto.matricNo() == null ? id : dto.matricNo()) + "-" + dto.meetingDate() + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .contentType(MediaType.APPLICATION_PDF)
                .body(bytes);
    }

    @GetMapping("/drafts")
    public List<MeetingSummary> drafts() {
        return meetings.drafts(current.get());
    }

    @GetMapping("/deleted")
    public List<MeetingSummary> deleted() {
        return meetings.deleted(current.get());
    }

    static String etag(Integer version) {
        return "\"" + (version == null ? 0 : version) + "\"";
    }

    static Integer parseIfMatch(String ifMatch) {
        if (ifMatch == null || ifMatch.isBlank() || "*".equals(ifMatch.trim())) {
            return null;
        }
        String v = ifMatch.trim().replace("W/", "").replace("\"", "");
        try {
            return Integer.valueOf(v);
        } catch (NumberFormatException e) {
            throw new uk.ac.dundee.ga.mms.web.error.ApiException(HttpStatus.PRECONDITION_FAILED, "version-mismatch",
                    "Invalid If-Match header");
        }
    }
}
