package cheer.common.result;

import cheer.common.enums.ResultCode;
import lombok.Data;

/**
 * 统一响应封装
 *
 * @param <T> 响应数据类型
 */
@Data
public class Result<T> {
    /** 响应数据 */
    private T data;
    /** 状态码 */
    private int code;
    /** 提示信息 */
    private String message;

    public Result(T data, int code, String message) {
        this.data = data;
        this.code = code;
        this.message = message;
    }

    public Result(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public Result() {
    }

    /** 成功响应（无数据） */
    public static <T> Result<T> success() {
        return new Result<>();
    }

    /** 成功响应（带数据） */
    public static <T> Result<T> success(T data, ResultCode code, String message) {
        return new Result<>(data, code.getCode(), message);
    }

    /** 失败响应 */
    public static <T> Result<T> error(ResultCode code, String message) {
        return new Result<>(code.getCode(), message);
    }

    /** 失败响应（无具体信息） */
    public static <T> Result<T> error() {
        return new Result<>();
    }
}
