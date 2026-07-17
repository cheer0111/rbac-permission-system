package cheer.common.exception;

import cheer.common.enums.ResultCode;

/**
 * 业务异常（可预期的异常，如参数错误、数据已存在等）
 * <p>
 * 由 GlobalExceptionHandler 统一捕获并返回对应的 HTTP 状态码和提示信息
 */
public class BusinessException extends RuntimeException {
    /** 异常状态码 */
    private int code;

    /** 使用 ResultCode 枚举构造 */
    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    /** 使用 ResultCode 枚举 + 自定义消息构造 */
    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    /** 只传消息，默认 code=500 */
    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}
