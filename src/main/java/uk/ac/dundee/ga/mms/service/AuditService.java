package uk.ac.dundee.ga.mms.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import uk.ac.dundee.ga.mms.config.MmsProperties;
import uk.ac.dundee.ga.mms.domain.AppUser;
import uk.ac.dundee.ga.mms.domain.AuditEvent;
import uk.ac.dundee.ga.mms.storage.AuditStore;
import uk.ac.dundee.ga.mms.storage.PageResult;
import uk.ac.dundee.ga.mms.storage.UserStore;
import uk.ac.dundee.ga.mms.util.Csv;
import uk.ac.dundee.ga.mms.util.Hashing;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Writes and reads the append-only audit trail. Never records notes text. */
@Slf4j
@Service
public class AuditService {

    private final AuditStore store;
    private final UserStore users;
    private final MmsProperties props;
    private final ObjectMapper json;

    public AuditService(AuditStore store, UserStore users, MmsProperties props, ObjectMapper json) {
        this.store = store;
        this.users = users;
        this.props = props;
        this.json = json;
    }

    public void record(String action, String entityType, Object entityId, Map<String, ?> details) {
        Long userId = currentUserId();
        String email = userId == null ? null : users.findById(userId).map(AppUser::getEmail).orElse(null);
        record(userId, email, action, entityType, entityId, details);
    }

    public void record(Long userId, String email, String action, String entityType, Object entityId, Map<String, ?> details) {
        try {
            AuditEvent e = new AuditEvent();
            e.setOccurredAt(Instant.now());
            e.setUserId(userId);
            e.setUserEmail(email);
            e.setAction(action);
            e.setEntityType(entityType);
            e.setEntityId(entityId == null ? null : String.valueOf(entityId));
            e.setIpHash(ipHash());
            if (details != null && !details.isEmpty()) {
                String d = json.writeValueAsString(details);
                e.setDetailsJson(d.length() > 2000 ? d.substring(0, 2000) : d);
            }
            store.append(e);
        } catch (Exception ex) {
            log.error("Failed to write audit event {}", action, ex);
        }
    }

    public PageResult<AuditEvent> search(AuditStore.AuditQuery q, int page, int size) {
        return store.search(q, page, size);
    }

    public String exportCsv(AuditStore.AuditQuery q) {
        StringBuilder sb = new StringBuilder(Csv.row(List.of("occurred_at", "user_email", "action", "entity_type", "entity_id", "details")));
        for (AuditEvent e : store.searchAll(q)) {
            sb.append(Csv.row(java.util.Arrays.asList(e.getOccurredAt(), e.getUserEmail(), e.getAction(),
                    e.getEntityType(), e.getEntityId(), e.getDetailsJson())));
        }
        return sb.toString();
    }

    private Long currentUserId() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || a.getName() == null) {
            return null;
        }
        try {
            return Long.valueOf(a.getName());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String ipHash() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes sra) {
            HttpServletRequest req = sra.getRequest();
            String fwd = req.getHeader("X-Forwarded-For");
            String ip = fwd != null && !fwd.isBlank() ? fwd.substring(fwd.lastIndexOf(',') + 1).trim() : req.getRemoteAddr();
            return Hashing.sha256(props.getIpHashSalt() + ":" + ip);
        }
        return null;
    }
}
