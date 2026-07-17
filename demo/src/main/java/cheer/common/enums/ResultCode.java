package cheer.common.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 统一响应状态码枚举
 */
@Getter
@AllArgsConstructor
public enum ResultCode {

    SUCCESS(200, "请求成功"),
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    NOT_FOUND(404, "资源未找到"),
    DATA_EXISTS(409, "数据已存在"),
    SERVER_ERROR(500, "服务器内部错误");

    /** HTTP 状态码 */
    private final Integer code;
    /** 提示信息 */
    private final String message;
}
