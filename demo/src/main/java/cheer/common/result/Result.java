package cheer.common.result;

import cheer.common.enums.ResultCode;
import lombok.Data;

@Data
public class Result<T> {
    private T data;
    private int code;
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

    public static <T> Result<T> success() {
        return new Result<>();
    }

    public static <T> Result<T> success(T data, ResultCode code, String message) {
        return new Result<>(data, code.getCode(), message);
    }

    public static <T> Result<T> error(ResultCode code, String message) {
        return new Result<>(code.getCode(), message);
    }

    public static <T> Result<T> error() {
        return new Result<>();
    }
}
