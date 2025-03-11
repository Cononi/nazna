package io.github.cononi.core.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates that a method produces a bean to be managed by the NaznaContext.
 * The bean will be registered under the name specified by the value attribute,
 * or if not specified, under the name of the method.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Bean {

    /**
     * The name of the bean.
     * If not specified, the bean name will be the name of the method.
     */
    String value() default "";
}