package cheer.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicate {
    String key();

    long expireSeconds() default 5;

    String message() default "操作太频繁，请勿重复提交";
}
