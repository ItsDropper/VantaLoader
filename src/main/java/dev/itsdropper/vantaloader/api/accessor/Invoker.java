package dev.itsdropper.vantaloader.api.accessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Declares a method which invokes a target method, including non-public methods. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Invoker {
    /** Target method name; when empty, it is inferred from invokeX. */
    String value() default "";
}
