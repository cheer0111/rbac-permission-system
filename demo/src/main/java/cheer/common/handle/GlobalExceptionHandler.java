package cheer.common.handle;

import cheer.common.enums.ResultCode;
import cheer.common.exception.BusinessException;
import cheer.common.result.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

/**
 * 全局异常处理器
 * <p>
 * 按优先级捕获：BusinessException（业务异常）→ MethodArgumentNotValidException（参数校验异常）→ Exception（兜底）
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常（参数错误、数据已存在、权限不足等）
     */
    @ExceptionHandler(BusinessException.class)
    public Result<?> handleBusinessException(BusinessException e) {
        return new Result<>(e.getCode(), e.getMessage());
    }

    /**
     * 参数校验异常（@Valid 触发，返回每个字段的错误信息）
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<?> handleValidException(MethodArgumentNotValidException e) {
        Map<String, String> errors = new HashMap<>();
        e.getBindingResult().getFieldErrors().forEach(error -> {
            errors.put(error.getField(), error.getDefaultMessage());
        });
        return new Result<>(
                errors,
                ResultCode.PARAM_ERROR.getCode(),
                "参数校验失败"
        );
    }

    /**
     * 兜底异常处理（不暴露具体错误信息给前端）
     */
    @ExceptionHandler(Exception.class)
    public Result<?> handleException(Exception e) {
        return new Result<>(
                null,
                ResultCode.SERVER_ERROR.getCode(),
                ResultCode.SERVER_ERROR.getMessage()
        );
    }
}
