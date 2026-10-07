package uk.ac.dundee.ga.mms.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful completion is written to the audit log (FR-25).
 * The entity id is taken from the return value's {@code getId()} or else the first Long argument.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();

    String entity();
}
