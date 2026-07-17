package cheer.common.aspect;

import cheer.common.annotation.PreventDuplicate;
import cheer.common.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class PreventDuplicateAspect {

    private static final String REDIS_KEY_PREFIX = "prevent:duplicate:";

    @Autowired
    RedissonClient redissonClient;

    SpelExpressionParser spelExpressionParser = new SpelExpressionParser();
    DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(preventDuplicate)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicate preventDuplicate) throws Throwable {
        String redisKey = REDIS_KEY_PREFIX + generateKey(joinPoint, preventDuplicate);

        RLock lock = redissonClient.getLock(redisKey);

        // waitTime = 0：完全不等待，抢不到锁立刻返回 false，这正是"防重复提交"要的语义
        //   （如果 waitTime > 0，第二次请求会傻等，业务上通常不是我们想要的）
        // leaseTime = expireSeconds()：锁的存活时间，也就是"冷却期"时长
        // 注意：这里不做 finally { lock.unlock() }！
        //   一旦提前 unlock，冷却期就被人为打断了，起不到防重复的效果，
        //   必须让锁自己靠 leaseTime 过期
        boolean acquired = lock.tryLock(0, preventDuplicate.expireSeconds(), TimeUnit.SECONDS);

        if (!acquired) {
            log.warn("重复请求被拦截, key: {}", redisKey);
            throw new BusinessException(preventDuplicate.message());
        }

        try {
            return joinPoint.proceed();
        } catch (Throwable e) {
            // 业务执行失败时主动释放锁，允许用户立即重试，而不是白白等满冷却期
            // 注意：只能自己持有的锁才能 unlock，异常发生时当前线程一定是持有者，
            // 所以这里直接 unlock 是安全的
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
            throw e;
        }
        // 正常执行成功：不 unlock，让锁保留到 leaseTime 到期，
        // 这段时间内的重复请求都会在 tryLock 处直接被拒绝
    }

    /**
     * 根据 SpEL 表达式 + 方法签名生成唯一 key
     * 默认策略：类名 + 方法名 + 参数解析结果
     */
    private String generateKey(ProceedingJoinPoint joinPoint, PreventDuplicate preventDuplicate) {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = method.getName();

        String spel = preventDuplicate.key();
        if (spel == null || spel.isEmpty()) {
            // 没有指定 key 表达式时，退化为按方法 + 参数值区分
            Object[] args = joinPoint.getArgs();
            StringBuilder sb = new StringBuilder(className).append(":").append(methodName);
            for (Object arg : args) {
                sb.append(":").append(arg);
            }
            return sb.toString();
        }

        EvaluationContext context = new StandardEvaluationContext();
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        Object[] args = joinPoint.getArgs();
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
        }

        Expression expression = spelExpressionParser.parseExpression(spel);
        Object value = expression.getValue(context);

        return className + ":" + methodName + ":" + value;
    }
}