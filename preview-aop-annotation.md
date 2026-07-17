# Spring Boot 进阶知识点 — 自定义注解 + AOP 切面（含防重复提交完整实现）

> 本文档为预习材料，包含自定义注解、AOP 切面的从零实现，以及相关 Spring Boot 知识点。

---

## 第一章：什么是注解（Annotation）？

### 1.1 注解的本质

注解不是魔法——它本质上是一个**继承了 `java.lang.annotation.Annotation` 接口的特殊接口**。

当你写：

```java
@Override
public String toString() { ... }
```

编译后，`@Override` 变成了一个接口：

```java
public interface Override extends Annotation {
    // 没有任何方法 = 标记注解（Marker Annotation）
}
```

当你写：

```java
@RequestMapping("/user")
public class UserController { ... }
```

编译后：

```java
public @interface RequestMapping {
    String value() default "";
    String method() default "";
    // ...
}
```

**注解的要素：**

| 要素 | 说明 | 示例 |
|---|---|---|
| **保留策略** (`@Retention`) | 注解保留到哪个阶段 | SOURCE / CLASS / RUNTIME |
| **目标** (`@Target`) | 注解能放在哪里 | METHOD / FIELD / PARAMETER / TYPE |
| **属性** | 注解的参数 | `value()`, `message()` 等 |
| **默认值** | 属性的默认值 | `default ""` |

### 1.2 保留策略（@Retention）— 这是最重要的概念

```
源码阶段(.java) ──编译──→ 字节码阶段(.class) ──类加载──→ 运行时(JVM内存)
     │                        │                       │
     │                  RetentionPolicy           RetentionPolicy
     │                  .SOURCE = 到这里没了         .CLASS = 到这里没了
     │                                          RetentionPolicy
     │                                          .RUNTIME = 一直保留在内存
```

| 策略 | 保留到哪个阶段 | 用途 | 示例 |
|---|---|---|---|
| `SOURCE` | 只在源码中，编译后丢弃 | 给编译器看 | `@Override`, `@SuppressWarnings` |
| `CLASS` | 保留到字节码，运行时丢弃 | 给编译器和工具看 | MyBatis 的 `@Mapper` |
| **`RUNTIME`** | **保留到运行时，可通过反射读取** | **给程序在运行时判断** | **`@PreAuthorize`, `@Transactional`** |

**关键理解：** 如果你要写一个注解让 AOP 在运行时判断，**必须用 `@Retention(RetentionPolicy.RUNTIME)`**。否则运行时通过反射读不到这个注解。

### 1.3 目标（@Target）— 注解能放在哪里

```java
@Target(ElementType.METHOD)                    // 只能放在方法上
@Target(ElementType.TYPE)                     // 只能放在类/接口上
@Target({ElementType.METHOD, ElementType.TYPE}) // 方法或类都可以
```

| ElementType | 放在哪里 |
|---|---|
| `TYPE` | 类、接口、枚举 |
| `FIELD` | 字段 |
| `METHOD` | 方法 |
| `PARAMETER` | 方法参数 |
| `CONSTRUCTOR` | 构造方法 |
| `LOCAL_VARIABLE` | 局部变量 |

---

## 第二章：从零写一个自定义注解

### 2.1 需求

我们要写一个 `@PreventDuplicate` 注解，加在 Controller 方法上，实现"防重复提交"功能。

使用效果：

```java
@PostMapping("/add")
@PreventDuplicate(key = "#dto.username", expireSeconds = 5)
public Result<User> addUser(UserDTO dto) { ... }
```

含义：同一个 username 在 5 秒内只能提交一次。

### 2.2 定义注解

```java
package cheer.common.annotation;

import java.lang.annotation.*;

/**
 * 防重复提交注解
 * 加在 Controller 方法上，短时间内相同参数的请求会被拦截
 */
@Target(ElementType.METHOD)                    // 只能放在方法上
@Retention(RetentionPolicy.RUNTIME)            // 保留到运行时（AOP 需要反射读取）
@Documented                                     // 出现在 JavaDoc 中
public @interface PreventDuplicate {

    /**
     * 锁的 key，支持 SpEL 表达式
     * #dto.username → 从方法参数 dto 中取 username 字段
     * #userId        → 从方法参数 userId 取值
     */
    String key();

    /**
     * 锁的过期时间（秒）
     * 超过这个时间，相同 key 的请求可以再次通过
     */
    long expireSeconds() default 5;

    /**
     * 获取锁失败时的提示信息
     */
    String message() default "操作太频繁，请勿重复提交";
}
```

**逐行解析：**

| 要素 | 值 | 为什么 |
|---|---|---|
| `@Target` | `METHOD` | 只加在方法上（Controller 的接口方法） |
| `@Retention` | `RUNTIME` | AOP 切面需要在运行时通过反射读取这个注解的属性 |
| `@Documented` | — | 好习惯，让注解出现在 JavaDoc 中 |
| `key()` | 无默认值，必须填 | 告诉注解"用什么作为锁的标识" |
| `expireSeconds()` | `default 5` | 有默认值，不填就是 5 秒 |
| `message()` | `default "操作太频繁..."` | 有默认值，可覆盖 |

**注解的属性语法规则：**
- 没有默认值的属性，使用时**必须填**：`@PreventDuplicate(key = "...")`
- 有默认值的属性，使用时可以不填：`@PreventDuplicate(key = "...", expireSeconds = 10)`
- 如果只有一个属性且名叫 `value()`，使用时可以省略属性名：`@PreventDuplicate("key")`
- 但我们的主要属性叫 `key()`，所以必须写属性名

---

## 第三章：什么是 AOP？

### 3.1 核心概念

AOP（Aspect-Oriented Programming，面向切面编程）。

**类比：** 想象你在看一本书。每一页书的内容就是你的业务代码（增删改查）。AOP 就像是给每一页书套上一个透明的书签——书签上写着"阅读时间"。你不需要修改书的内容，就能在每一页上记录阅读时间。

```
┌─────────────────────────────────────────────┐
│              AOP 切面（Aspect）              │
│  ┌─────────┐  ┌─────────┐  ┌─────────┐     │
│  │ 前置通知 │  │ 环绕通知 │  │ 后置通知 │     │
│  │  Before │  │ Around  │  │  After  │     │
│  └────┬────┘  └────┬────┘  └────┬────┘     │
│       │            │            │           │
├───────┼────────────┼────────────┼───────────┤
│       ▼            ▼            ▼           │
│  ┌─────────────────────────────────┐        │
│  │     业务方法（被增强的方法）      │        │
│  │     addUser() / delete() / ...  │        │
│  └─────────────────────────────────┘        │
└─────────────────────────────────────────────┘
```

### 3.2 AOP 术语（这些词看起来吓人，其实很简单）

| 术语 | 一句话解释 | 类比 |
|---|---|---|
| **Aspect（切面）** | 横切逻辑的集合（一个类） | 一本透明书签 |
| **Join Point（连接点）** | 程序执行中的一个点（方法调用） | 书的每一页 |
| **Pointcut（切入点）** | 匹配哪些连接点要被拦截 | 只给某些章节加书签 |
| **Advice（通知）** | 在切入点前后要做什么 | 书签上写的内容 |
| **Target（目标对象）** | 被增强的原始对象 | 原来的书 |

### 3.3 通知类型

```java
@Before          // 方法执行前
@AfterReturning  // 方法正常返回后
@AfterThrowing   // 方法抛异常后
@After           // 方法执行后（无论成功还是异常）
@Around          // 环绕通知（最强大，可以决定是否执行方法）
```

**为什么 `@Around` 最常用？**

因为它可以在方法执行**前后**都加逻辑，还能决定**是否执行原方法**：

```java
@Around("pointcut()")
public Object around(ProceedingJoinPoint pjp) throws Throwable {
    // 前置逻辑
    System.out.println("方法执行前");

    // 执行原方法
    Object result = pjp.proceed();

    // 后置逻辑
    System.out.println("方法执行后，返回值: " + result);

    return result;       // 可以修改返回值
    // 不调用 pjp.proceed() = 不执行原方法（拦截）
}
```

---

## 第四章：SpEL 表达式解析

### 4.1 什么是 SpEL？

SpEL（Spring Expression Language）是 Spring 的表达式语言。你其实已经在用但可能没意识到：

```java
@Value("${spring.datasource.url}")     // ${} 是属性占位符，不是 SpEL
@Value("#{systemProperties['user.home']}")  // #{} 是 SpEL 表达式
```

在我们的防重复提交注解中，`key` 属性需要支持：

```java
@PreventDuplicate(key = "#dto.username")
```

这里 `#dto.username` 是 SpEL 表达式，意思是"从方法参数中找到名为 `dto` 的参数，取它的 `username` 字段"。

### 4.2 SpEL 解析工具

```java
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

// 解析器
ExpressionParser parser = new SpelExpressionParser();

// 上下文（存放变量）
StandardEvaluationContext context = new StandardEvaluationContext();
context.setVariable("dto", userDTO);  // 把方法参数放入上下文

// 解析表达式
String value = parser.parseExpression("#dto.username").getValue(context, String.class);
// 等价于: userDTO.getUsername()
```

### 4.3 从 Method 获取参数名的困难

在 AOP 切面中，你拿到的是 `Method` 对象和 `Object[] args`（参数值数组）。问题是：

```java
// 你拿到的：
Object[] args = [UserDTO(username="zhangsan", password="123456")]

// 你需要把 args 和参数名对应起来：
// args[0] 对应参数名 "dto"（因为方法签名是 addUser(UserDTO dto)）
```

但 Java 编译后默认**不保留参数名**，`method.getParameters()` 拿到的是 `arg0, arg1...`。

**解决方案：**

1. 编译时加 `-parameters` 参数（Maven compiler 插件）
2. 用 Spring 的 `LocalVariableTableParameterNameDiscoverer`（旧版）
3. 用 Spring 的 `DefaultParameterNameDiscoverer`（推荐）

```java
DefaultParameterNameDiscoverer discoverer = new DefaultParameterNameDiscoverer();
String[] paramNames = discoverer.getParameterNames(method);
// paramNames = ["dto"]  ← 拿到了参数名
```

然后就可以把参数名和参数值对应起来，放入 SpEL 上下文：

```java
StandardEvaluationContext context = new StandardEvaluationContext();
for (int i = 0; i < paramNames.length; i++) {
    context.setVariable(paramNames[i], args[i]);
}
// context 中: "dto" → UserDTO(username="zhangsan")
```

---

## 第五章：完整实现 — 防重复提交注解 + AOP 切面

### 5.1 实现步骤总览

```
1. 定义注解 @PreventDuplicate
2. 写 AOP 切面 PreventDuplicateAspect
   - 获取方法上的注解
   - 用 SpEL 解析 key
   - 用 Redisson 加分布式锁
   - 加锁失败 → 拦截请求
   - 加锁成功 → 执行方法 → 释放锁
3. 在 Controller 方法上使用 @PreventDuplicate
```

### 5.2 完整代码

#### 注解定义（你已经看过了，这里再完整贴一遍）

```java
// common/annotation/PreventDuplicate.java
package cheer.common.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface PreventDuplicate {
    String key();
    long expireSeconds() default 5;
    String message() default "操作太频繁，请勿重复提交";
}
```

#### AOP 切面

```java
// common/aspect/PreventDuplicateAspect.java
package cheer.common.aspect;

import cheer.common.annotation.PreventDuplicate;
import cheer.common.exceptions.BusinessException;
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

import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class PreventDuplicateAspect {

    @Autowired
    private RedissonClient redissonClient;

    private final SpelExpressionParser spelParser = new SpelExpressionParser();
    private final DefaultParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 环绕通知：拦截所有带 @PreventDuplicate 注解的方法
     *
     * @annotation(preventDuplicate) 是切入点表达式：
     *   @annotation(...) = 匹配方法上带有指定注解的方法
     *   preventDuplicate = 把注解实例注入到方法参数中
     */
    @Around("@annotation(preventDuplicate)")
    public Object around(ProceedingJoinPoint joinPoint, PreventDuplicate preventDuplicate) throws Throwable {

        // ========== 第 1 步：解析 SpEL 表达式，获取锁的 key ==========
        String keyExpression = preventDuplicate.key();
        String lockKey = "lock:" + parseSpelKey(keyExpression, joinPoint);

        // ========== 第 2 步：获取 Redisson 分布式锁 ==========
        RLock lock = redissonClient.getLock(lockKey);

        try {
            // tryLock 参数：等待时间=0（不等待），锁持有时间=expireSeconds
            boolean acquired = lock.tryLock(0, preventDuplicate.expireSeconds(), TimeUnit.SECONDS);

            if (!acquired) {
                // 加锁失败 = 已经有相同 key 的请求在执行 = 重复提交
                log.warn("防重复提交拦截: key={}, message={}", lockKey, preventDuplicate.message());
                throw new BusinessException(preventDuplicate.message());
            }

            // ========== 第 3 步：加锁成功，执行原方法 ==========
            return joinPoint.proceed();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        } finally {
            // ========== 第 4 步：释放锁 ==========
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 解析 SpEL 表达式，从方法参数中提取实际值
     *
     * 例如：@PreventDuplicate(key = "#dto.username")
     *   方法签名：addUser(UserDTO dto)
     *   实际参数：dto = UserDTO(username="zhangsan")
     *   解析结果："zhangsan"
     *
     * 返回值示例："zhangsan" 或 "1943123456789012001"
     */
    private String parseSpelKey(String keyExpression, ProceedingJoinPoint joinPoint) {
        // 1. 获取方法签名
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        java.lang.reflect.Method method = signature.getMethod();

        // 2. 获取参数名数组：["dto", "id"]
        String[] paramNames = nameDiscoverer.getParameterNames(method);
        if (paramNames == null || paramNames.length == 0) {
            return keyExpression;  // 没有参数名，直接返回表达式本身
        }

        // 3. 获取参数值数组：[UserDTO(...), Long(...)]
        Object[] args = joinPoint.getArgs();

        // 4. 构建 SpEL 上下文：把 "dto" → UserDTO(...) 放入
        EvaluationContext context = new StandardEvaluationContext();
        for (int i = 0; i < paramNames.length; i++) {
            context.setVariable(paramNames[i], args[i]);
        }

        // 5. 解析表达式
        try {
            Expression expression = spelParser.parseExpression(keyExpression);
            Object value = expression.getValue(context);
            return value != null ? value.toString() : keyExpression;
        } catch (Exception e) {
            log.warn("SpEL 解析失败: expression={}, error={}", keyExpression, e.getMessage());
            return keyExpression;  // 解析失败，返回表达式本身
        }
    }
}
```

**逐行解析关键代码：**

#### 切入点表达式

```java
@Around("@annotation(preventDuplicate)")
```

| 部分 | 含义 |
|---|---|
| `@Around` | 环绕通知，可以在方法前后加逻辑 |
| `@annotation(...)` | 匹配方法上**带有指定注解**的方法 |
| `preventDuplicate` | 注解实例，会注入到方法参数中 |

#### tryLock 三个参数

```java
lock.tryLock(0, preventDuplicate.expireSeconds(), TimeUnit.SECONDS);
```

| 参数 | 值 | 含义 |
|---|---|---|
| waitTime | 0 | 不等待。拿不到锁立即返回 false |
| leaseTime | expireSeconds | 锁自动释放时间。防止死锁 |
| unit | SECONDS | 时间单位 |

**为什么不等待（waitTime=0）？**

防重复提交的场景：用户快速双击 → 第二次请求应该**立即被拒绝**，而不是排队等第一次完成。

#### isHeldByCurrentThread

```java
if (lock.isHeldByCurrentThread()) {
    lock.unlock();
}
```

**为什么不能直接 unlock？**

如果当前线程没有持有锁（比如 tryLock 返回 false），直接 unlock 会抛 `IllegalMonitorStateException`。`isHeldByCurrentThread` 确保只释放自己持有的锁。

#### finally 块

```java
finally {
    if (lock.isHeldByCurrentThread()) {
        lock.unlock();
    }
}
```

**为什么必须在 finally 里释放？**

如果 `joinPoint.proceed()` 抛异常，不进 finally 的话锁就不会释放 → 其他请求永远拿不到锁 → 死锁。

### 5.3 在 Controller 中使用

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @PostMapping("/add")
    @PreventDuplicate(key = "#dto.username", expireSeconds = 5)
    Result<User> addUser(UserDTO dto) {
        User user = userService.add(dto);
        return Result.success(user, ResultCode.SUCCESS, "插入成功");
    }

    @DeleteMapping("/delete/{id}")
    @PreventDuplicate(key = "#id", expireSeconds = 3)
    public Result deleteById(@PathVariable Long id) {
        userService.delete(id);
        return Result.success(id, ResultCode.SUCCESS, "删除成功");
    }
}
```

**使用效果：**

| 操作 | 结果 |
|---|---|
| POST /user/add (username=zhangsan) | 第一次 → 200 成功 |
| POST /user/add (username=zhangsan)，0.5 秒内 | 第二次 → 被拦截，返回"操作太频繁" |
| POST /user/add (username=lisi) | 可以执行（不同的 key） |
| POST /user/add (username=zhangsan)，6 秒后 | 可以执行（锁已过期） |

### 5.4 Maven 编译参数（保留参数名）

为了让 `DefaultParameterNameDiscoverer` 能获取到参数名（`dto` 而不是 `arg0`），需要在 pom.xml 中配置：

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <parameters>true</parameters>    <!-- 保留参数名 -->
        <source>17</source>
        <target>17</target>
    </configuration>
</plugin>
```

或者更简单：Spring Boot 3.x 默认已经开启了 `-parameters`，你大概率不需要额外配置。

---

## 第六章：执行流程图

```
用户快速双击"新增"按钮
    │
    ├─ 请求 A（第一次）                    请求 B（第二次，0.3秒后）
    │     │                                   │
    │     ▼                                   ▼
    │  Controller.addUser(dto)          Controller.addUser(dto)
    │     │                                   │
    │     ▼                                   ▼
    │  PreventDuplicateAspect               PreventDuplicateAspect
    │     │                                   │
    │     ├─ 解析 SpEL：#dto.username       ├─ 解析 SpEL：#dto.username
    │     │  → "zhangsan"                     │  → "zhangsan"
    │     │                                   │
    │     ├─ getLock("lock:zhangsan")        ├─ getLock("lock:zhangsan")
    │     │                                   │
    │     ├─ tryLock → true ✅               ├─ tryLock → false ❌
    │     │                                   │
    │     ├─ 执行 addUser()                  ├─ throw BusinessException
    │     │   → INSERT 成功                  │   → "操作太频繁，请勿重复提交"
    │     │                                   │
    │     ├─ unlock ✅                         │
    │     │                                   │
    │     ▼                                   ▼
    │  返回 200                             返回 500（BusinessException）
    │
    ▼
用户只看到第一次成功，第二次提示"操作太频繁"
```

---

## 第七章：其他 Spring Boot 实用知识点

### 7.1 @Transactional 事务注解

```java
@Service
public class UserServiceImpl implements UserService {

    @Transactional  // 加上这个注解，方法内的所有数据库操作要么全成功，要么全回滚
    public void transfer(Long fromId, Long toId, BigDecimal amount) {
        // 扣款
        accountMapper.deduct(fromId, amount);
        // 加款（如果这里报错，上面的扣款也会回滚）
        accountMapper.add(toId, amount);
    }
}
```

**为什么需要事务？**

假设扣款成功了，但加款时网络断了。用户的钱被扣了但没到账。

**@Transactional 的原理也是 AOP。** Spring 在方法执行前开启事务，方法正常结束提交事务，方法抛异常回滚事务。

**注意：@Transactional 只在 public 方法上生效。** 因为 Spring AOP 是基于代理的，private 方法不会被代理增强。

### 7.2 @Async 异步执行

```java
@Service
public class EmailService {

    @Async  // 这个方法会在独立的线程池中执行，不阻塞主线程
    public void sendEmail(String to, String subject, String content) {
        // 模拟发送邮件需要 5 秒
        Thread.sleep(5000);
        System.out.println("邮件发送成功: " + to);
    }
}

@RestController
public class OrderController {

    @Autowired
    EmailService emailService;

    @PostMapping("/order")
    public Result createOrder() {
        orderService.create();
        emailService.sendEmail("user@test.com", "订单确认", "...");
        return Result.success("下单成功");  // 不会等邮件发完才返回
    }
}
```

**注意：** `@Async` 需要在配置类上加 `@EnableAsync` 才生效（和 `@EnableMethodSecurity` 类似）。

### 7.3 全局异常处理的顺序

你项目里已经有 `GlobalExceptionHandler`，它用 `@RestControllerAdvice` + `@ExceptionHandler` 拦截异常。

当多个 `@ExceptionHandler` 都能处理同一个异常时，Spring 会选择**最具体**的那个：

```java
@ExceptionHandler(BusinessException.class)   // 具体异常 → 优先
public Result<?> handleBusinessException(BusinessException e) { ... }

@ExceptionHandler(Exception.class)           // 通用异常 → 兜底
public Result<?> handleException(Exception e) { ... }
```

**顺序规则：** 子类异常的 Handler 优先于父类异常的 Handler。所以 `BusinessException` 的 Handler 会优先于 `Exception` 的 Handler。

### 7.4 Spring Boot 自动配置原理（面试高频）

Spring Boot 启动时会扫描 classpath 下的 `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` 文件（Spring Boot 3.x）或 `META-INF/spring.factories` 文件（Spring Boot 2.x），加载所有自动配置类。

每个自动配置类用 `@ConditionalOnXxx` 注解决定是否生效：

```java
@Configuration
@ConditionalOnClass(RedisTemplate.class)        // classpath 里有 RedisTemplate 才生效
@ConditionalOnProperty(prefix = "spring.data.redis", name = "host")  // yml 里有配置才生效
public class RedisAutoConfiguration {
    @Bean
    public RedisTemplate<?, ?> redisTemplate(...) { ... }
}
```

**这就是为什么你只加了 `spring-boot-starter-data-redis` 依赖 + yml 配置，RedisTemplate 就能直接注入使用——Spring Boot 自动帮你配好了。**

同理，你加 `redisson-spring-boot-starter` 后，`RedissonClient` 也能直接注入。

### 7.5 Bean 的作用域

| 作用域 | 实例数量 | 适用场景 |
|---|---|---|
| **singleton**（默认） | 整个容器只有一个 | Service、Mapper、RedisTemplate |
| **prototype** | 每次注入创建新的 | 不推荐，容易和 AOP/事务冲突 |
| **request** | 每个 HTTP 请求一个 | 请求级别的上下文信息 |
| **session** | 每个 Session 一个 | 用户登录信息（前后端分离一般不用） |

**你的项目中几乎所有 Bean 都是 singleton**——这也是为什么 `UserService` 可以直接 `@Autowired` 注入到 Controller，因为整个应用只有一个实例，线程安全由数据库和 Redis 保证。

### 7.6 循环依赖问题

如果 A 依赖 B，B 又依赖 A，Spring 启动时会报 `CircularDependencyException`。

```java
@Service
public class UserService {
    @Autowired RoleService roleService;  // UserService → RoleService
}

@Service
public class RoleService {
    @Autowired UserService userService;  // RoleService → UserService → 循环！
}
```

**解决方案（按优先级）：**
1. **重构代码**：把公共逻辑抽到第三个 Service 中，消除循环
2. **@Lazy 延迟注入**：`@Autowired @Lazy RoleService roleService;`（代理对象，首次使用时才初始化）
3. **setter 注入**：代替字段注入（Spring 可以解决 setter 注入的循环依赖）

---

## 第八章：知识点串联图

```
用户请求 POST /user/add {username: "zhangsan"}
    │
    ▼
Tomcat → Spring DispatcherServlet
    │
    ▼
Spring Security Filter Chain
    ├─ JwtAuthenticationFilter → 解析 token → SecurityContext
    │
    ▼
AOP 代理（@Aspect 生成的代理对象）
    ├─ PreventDuplicateAspect.around()
    │   ├─ 读取 @PreventDuplicate 注解属性
    │   ├─ SpEL 解析 #dto.username → "zhangsan"
    │   ├─ Redisson tryLock("lock:zhangsan")
    │   │   ├─ 成功 → 继续
    │   │   └─ 失败 → throw BusinessException → GlobalExceptionHandler → 返回 500
    │   │
    ▼
Controller.addUser(dto)
    │
    ▼
@Service（UserServiceImpl）
    ├─ @Transactional（如果加了的话，AOP 代理也会在这里）
    │   ├─ 开启事务
    │   ├─ selectCount → 查重
    │   ├─ insert → 入库
    │   └─ 提交事务
    │
    ▼
AOP 代理（回到 PreventDuplicateAspect）
    ├─ unlock("lock:zhangsan")
    │
    ▼
返回 Result → Jackson 序列化 JSON → HTTP 响应 200
```

你可以看到，一个请求经过了好几层 AOP 代理：JwtFilter（Servlet Filter）、Security Filter Chain、自定义 AOP 切面、@Transactional。每一层都在不修改业务代码的前提下，给请求加上了额外功能。这就是 AOP 的核心价值。

---

## 第九章：明天编码任务预览

1. **创建 `PreventDuplicate` 注解** — `common/annotation/PreventDuplicate.java`
2. **创建 `PreventDuplicateAspect` 切面** — `common/aspect/PreventDuplicateAspect.java`
3. **在 UserController 的 `addUser` 和 `deleteById` 上加 `@PreventDuplicate`**
4. **Postman 测试：快速双击同一个请求，验证第二次被拦截**
5. **在 RESP 里查看 `lock:xxx` 这个 key 的存在时间是否和 expireSeconds 一致**
6. **更新 PROGRESS.md** — Stage 5 完成

预计代码量不大（一个注解 ~20 行 + 一个切面 ~80 行），重点是理解 SpEL 解析和 AOP 切面的执行原理。
