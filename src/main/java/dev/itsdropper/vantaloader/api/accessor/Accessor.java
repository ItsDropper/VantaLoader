package dev.itsdropper.vantaloader.api.accessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a getter or setter backed by a field on the accessor's target object. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Accessor {
    /** Field name; when empty, it is inferred from getX, isX, or setX. */
    String value() default "";
}
