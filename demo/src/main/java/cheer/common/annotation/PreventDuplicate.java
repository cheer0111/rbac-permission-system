package cheer.common.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交注解
 * <p>
 * 加在 Controller 方法上，短时间内相同参数的请求会被 Redisson 分布式锁拦截
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicate {
    /**
     * 锁的 key，支持 SpEL 表达式
     * <p>
     * 示例：#dto.username → 从方法参数 dto 中取 username 字段
     */
    String key();
    /** 锁的过期时间（秒），即冷却期长度 */
    long expireSeconds() default 5;
    /** 获取锁失败时的提示信息 */
    String message() default "操作太频繁，请勿重复提交";
}
