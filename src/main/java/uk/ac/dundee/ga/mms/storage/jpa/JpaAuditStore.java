package uk.ac.dundee.ga.mms.storage.jpa;

import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.ac.dundee.ga.mms.domain.AuditEvent;
import uk.ac.dundee.ga.mms.storage.AuditStore;
import uk.ac.dundee.ga.mms.storage.PageResult;

import java.util.ArrayList;
import java.util.List;

@Repository
public class JpaAuditStore implements AuditStore {
    private final AuditRepository r;

    public JpaAuditStore(AuditRepository r) {
        this.r = r;
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void append(AuditEvent event) {
        r.save(event);
    }

    @Override
    public PageResult<AuditEvent> search(AuditQuery q, int page, int size) {
        Page<AuditEvent> p = r.findAll(spec(q), PageRequest.of(Math.max(page, 0), Math.min(Math.max(size, 1), 500),
                Sort.by(Sort.Direction.DESC, "occurredAt")));
        return new PageResult<>(p.getContent(), p.getNumber(), p.getSize(), p.getTotalElements(), p.getTotalPages());
    }

    @Override
    public List<AuditEvent> searchAll(AuditQuery q) {
        return r.findAll(spec(q), Sort.by(Sort.Direction.DESC, "occurredAt"));
    }

    private Specification<AuditEvent> spec(AuditQuery q) {
        return (root, query, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (q.from() != null) {
                ps.add(cb.greaterThanOrEqualTo(root.get("occurredAt"), q.from()));
            }
            if (q.to() != null) {
                ps.add(cb.lessThanOrEqualTo(root.get("occurredAt"), q.to()));
            }
            if (q.userId() != null) {
                ps.add(cb.equal(root.get("userId"), q.userId()));
            }
            if (q.entityType() != null && !q.entityType().isBlank()) {
                ps.add(cb.equal(root.get("entityType"), q.entityType()));
            }
            if (q.action() != null && !q.action().isBlank()) {
                ps.add(cb.equal(root.get("action"), q.action()));
            }
            return cb.and(ps.toArray(new Predicate[0]));
        };
    }
}
