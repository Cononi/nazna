package io.github.cononi.core.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface NaznaServerApplication {
    // With component scan package path
    String[] scanPackages() default {};

    // Is component Scanning Active
    boolean scanClasspath() default true;
}
