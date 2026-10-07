package uk.ac.dundee.ga.mms.storage;

import uk.ac.dundee.ga.mms.domain.AuditEvent;

import java.time.Instant;
import java.util.List;

public interface AuditStore {
    void append(AuditEvent event);

    PageResult<AuditEvent> search(AuditQuery query, int page, int size);

    List<AuditEvent> searchAll(AuditQuery query);

    record AuditQuery(Instant from, Instant to, Long userId, String entityType, String action) {
    }
}
