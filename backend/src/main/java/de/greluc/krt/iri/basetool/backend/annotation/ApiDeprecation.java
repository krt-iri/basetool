package de.greluc.krt.iri.basetool.backend.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark API endpoints or fields as deprecated and provide sunset information.
 * This will automatically set Sunset and Link HTTP headers via DeprecationInterceptor
 * and update the OpenAPI documentation.
 */
@Target({ElementType.METHOD, ElementType.TYPE, ElementType.FIELD})
@Retention(RetentionPolicy.RUNTIME)
public @interface ApiDeprecation {
    /**
     * The date when the endpoint or field will be permanently removed (Sunset).
     * Format: YYYY-MM-DD
     */
    String sunset() default "";

    /**
     * A URL or path to the replacement endpoint or documentation.
     * Used for the 'Link' HTTP header (rel="alternate").
     */
    String replacement() default "";
}
