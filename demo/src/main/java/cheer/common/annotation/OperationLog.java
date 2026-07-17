package cheer.common.annotation;

import java.lang.annotation.*;

/**
 * 操作日志注解
 * <p>
 * 加在 Controller 方法上，AOP 切面会自动拦截并记录操作日志到 sys_operate_log 表
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface OperationLog {
    /** 模块标题，如 "用户管理" */
    String title() default "";
    /** 操作类型：0=其他 1=新增 2=修改 3=删除 */
    int businessType();
    /** 是否保存请求参数（默认保存） */
    boolean isSaveRequestData() default true;
    /** 是否保存返回结果（默认保存） */
    boolean isSaveResponseData() default true;
}
