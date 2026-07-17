package cheer.common.exception;

import cheer.common.enums.ResultCode;

public class BusinessException extends RuntimeException {
    private int code;

    public BusinessException(ResultCode resultCode) {
        super(resultCode.getMessage());
        this.code = resultCode.getCode();
    }

    public BusinessException(ResultCode resultCode, String message) {
        super(message);
        this.code = resultCode.getCode();
    }

    public BusinessException(String message) {
        super(message);
        this.code = 500;
    }

    public int getCode() {
        return code;
    }
}
