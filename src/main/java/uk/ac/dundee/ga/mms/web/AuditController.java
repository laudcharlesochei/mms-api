package uk.ac.dundee.ga.mms.web;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.ac.dundee.ga.mms.service.AuditService;
import uk.ac.dundee.ga.mms.storage.AuditStore.AuditQuery;
import uk.ac.dundee.ga.mms.storage.PageResult;
import uk.ac.dundee.ga.mms.web.dto.AdminDtos.AuditDto;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasAnyRole('ADMIN','DIRECTOR')")
@Tag(name = "Audit", description = "Audit log (FR-25)")
public class AuditController {

    private final AuditService audit;

    public AuditController(AuditService audit) {
        this.audit = audit;
    }

    @GetMapping
    public PageResult<AuditDto> search(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                       @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                       @RequestParam(required = false) Long user,
                                       @RequestParam(required = false) String entity,
                                       @RequestParam(required = false) String action,
                                       @RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        return audit.search(query(from, to, user, entity, action), page, size)
                .map(e -> new AuditDto(e.getId(), e.getOccurredAt(), e.getUserId(), e.getUserEmail(), e.getAction(),
                        e.getEntityType(), e.getEntityId(), e.getDetailsJson()));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                         @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                         @RequestParam(required = false) Long user,
                                         @RequestParam(required = false) String entity,
                                         @RequestParam(required = false) String action) {
        AuditQuery q = query(from, to, user, entity, action);
        audit.record("EXPORT_AUDIT", "AuditEvent", null, Map.of());
        return CsvResponses.csv("mms-audit.csv", audit.exportCsv(q));
    }

    private static AuditQuery query(LocalDate from, LocalDate to, Long user, String entity, String action) {
        return new AuditQuery(from == null ? null : from.atStartOfDay().toInstant(ZoneOffset.UTC),
                to == null ? null : to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC), user, entity, action);
    }
}
