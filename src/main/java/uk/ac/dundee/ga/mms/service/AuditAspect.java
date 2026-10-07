package uk.ac.dundee.ga.mms.service;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Map;

/** Writes an audit event after any {@link Auditable} method returns successfully. */
@Aspect
@Component
public class AuditAspect {

    private final AuditService audit;

    public AuditAspect(AuditService audit) {
        this.audit = audit;
    }

    @AfterReturning(pointcut = "@annotation(auditable)", returning = "result")
    public void after(JoinPoint jp, Auditable auditable, Object result) {
        audit.record(auditable.action(), auditable.entity(), entityId(jp, result), Map.of());
    }

    private Object entityId(JoinPoint jp, Object result) {
        if (result != null) {
            try {
                Method m = result.getClass().getMethod("getId");
                Object id = m.invoke(result);
                if (id != null) {
                    return id;
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
            try {
                Method m = result.getClass().getMethod("id");
                Object id = m.invoke(result);
                if (id != null) {
                    return id;
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
        }
        for (Object a : jp.getArgs()) {
            if (a instanceof Long l) {
                return l;
            }
        }
        return null;
    }
}
