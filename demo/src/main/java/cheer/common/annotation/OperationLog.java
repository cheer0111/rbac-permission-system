package cheer.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String title() default "";       // 模块标题，如 "用户管理"

    int businessType();               // 操作类型：1=新增 2=修改 3=删除

    boolean isSaveRequestData() default true;   // 是否保存请求参数

    boolean isSaveResponseData() default true;  // 是否保存返回结果
}
