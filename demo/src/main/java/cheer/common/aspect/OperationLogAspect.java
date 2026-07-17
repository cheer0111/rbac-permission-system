package cheer.common.aspect;

import cheer.common.annotation.OperationLog;
import cheer.entity.OperateLog;
import cheer.service.OperateLogService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 操作日志 AOP 切面
 * <p>
 * 拦截所有带 @OperationLog 注解的方法，记录操作人、请求信息、参数、返回结果、耗时等，异步写入数据库。
 * 日志写入失败不影响主业务（try-catch 兜底），业务异常不被吞掉（重新抛出）。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    @Autowired
    private OperateLogService operateLogService;

    /** 自定义 ObjectMapper（注册 JavaTimeModule 支持 LocalDateTime） */
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OperationLogAspect() {
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 执行业务方法，捕获异常但不吞掉
        Object result = null;
        Throwable exception = null;
        try {
            result = joinPoint.proceed();
        } catch (Throwable e) {
            exception = e;
        }

        try {
            // 组装操作日志
            OperateLog operateLog = new OperateLog();
            // 从注解取模块信息
            operateLog.setTitle(operationLog.title());
            operateLog.setBusinessType(operationLog.businessType());

            // 方法全路径：类名.方法名
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String methodName = joinPoint.getTarget().getClass().getName() + "." + signature.getName();
            operateLog.setMethod(methodName);

            // 从请求上下文取 HTTP 信息
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) {
                HttpServletRequest request = attrs.getRequest();
                operateLog.setRequestMethod(request.getMethod());
                operateLog.setOperUrl(request.getRequestURL().toString());
                operateLog.setOperIp(request.getRemoteAddr());
            }

            // 从 SecurityContext 取当前操作人
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication != null) {
                operateLog.setOperName(authentication.getName());
            }

            // 序列化请求参数（过滤不可序列化对象 + password 脱敏）
            if (operationLog.isSaveRequestData()) {
                operateLog.setOperParam(toJsonString(joinPoint.getArgs(), signature));
            }

            // 序列化返回结果
            if (operationLog.isSaveResponseData() && result != null) {
                try {
                    operateLog.setOperResult(objectMapper.writeValueAsString(result));
                } catch (JsonProcessingException e) {
                    log.warn("操作日志返回结果序列化失败", e);
                }
            }

            // 状态：0=正常 1=异常
            if (exception != null) {
                operateLog.setStatus(1);
                operateLog.setErrorMsg(exception.getMessage());
            } else {
                operateLog.setStatus(0);
            }

            operateLog.setOperTime(LocalDateTime.now());
            operateLog.setCostTime(System.currentTimeMillis() - startTime);

            // 异步写入数据库（@Async 在 ServiceImpl 中）
            operateLogService.save(operateLog);
        } catch (Exception e) {
            // 日志组装/写入失败不能影响业务
            log.error("操作日志记录异常", e);
        }

        // 重新抛出业务异常
        if (exception != null) {
            throw exception;
        }
        return result;
    }

    /**
     * 序列化参数为 JSON
     * <p>
     * - 过滤 HttpServletRequest/Response/MultipartFile 等不可序列化对象
     * - 对 password/pwd 字段脱敏（替换为 ******）
     */
    private String toJsonString(Object[] args, MethodSignature signature) {
        try {
            Map<String, Object> map = new LinkedHashMap<>();
            String[] paramNames = signature.getParameterNames();
            if (paramNames == null) {
                paramNames = new String[0];
            }
            for (int i = 0; i < args.length; i++) {
                Object arg = args[i];
                // 过滤不可序列化的对象
                if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse
                        || arg instanceof MultipartFile) {
                    continue;
                }
                String name = i < paramNames.length ? paramNames[i] : "arg" + i;
                map.put(name, arg);
            }
            // password 脱敏
            if (map.containsKey("password") || map.containsKey("pwd")) {
                if (map.get("password") != null) {
                    map.put("password", "******");
                }
                if (map.get("pwd") != null) {
                    map.put("pwd", "******");
                }
            }
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.warn("操作日志参数序列化失败", e);
            return "";
        }
    }
}
