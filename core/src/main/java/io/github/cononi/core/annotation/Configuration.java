package io.github.cononi.core.annotation;

import java.lang.annotation.*;

/**
 * With annotation this component for settings
 */

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@Documented
@Component
public @interface Configuration {
    /**
     * The name of the configuration bean.
     * If not specified, the bean name will be the name of the class with first letter lowercase.
     */
    String value() default "";
}
