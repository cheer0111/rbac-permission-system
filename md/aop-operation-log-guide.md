# 阶段 6：AOP 操作日志 + Knife4j 接口文档

> 预习资料，动手前先通读。代码由你自己写，Claude 出设计 + review。

---

## 一、为什么需要操作日志？

企业系统里有一条硬性要求：**关键操作必须可追溯**。

比如管理员删除了一个用户、给某个角色分配了菜单——出了问题要能查到"谁、什么时候、做了什么、操作结果如何"。

操作日志和普通日志（`log.info`）的区别：

| | 普通日志（log4j/Slf4j） | 操作日志 |
|---|---|---|
| 存储位置 | 文件 | 数据库 |
| 写法 | `log.info("删除用户: {}", id)` | 自动拦截，开发者不用手动写 |
| 用途 | 排查 bug | 审计、追溯、合规 |
| 消费方 | 开发者看日志文件 | 管理后台页面查询 |

---

## 二、核心思路：自定义注解 + AOP

你已经做过 `@PreventDuplicate` + `PreventDuplicateAspect` 了，模式是一样的：

```
注解（声明） → AOP 切面（拦截） → 日志落库
```

只不过这次拦截的**不是加锁**，而是**记录日志**。

### 2.1 为什么用注解而不是切所有 Controller？

Plan 文件里提到了：

> AOP 切点尽量精确到"带有 `@OperationLog` 注解的方法"，不要笼统地切所有 Controller 方法

原因：
1. **性能**——每个请求都拦截会产生不必要的开销
2. **噪音**——查询接口不需要操作日志，全量切会产生大量无用数据
3. **灵活**——通过注解参数（如 `value = "新增用户"`）直接记录操作描述

### 2.2 操作日志要记录什么字段？

一张 `sys_operate_log` 表，字段设计如下：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGINT | 雪花 ID |
| title | VARCHAR(64) | 模块标题，如"用户管理" |
| business_type | TINYINT | 操作类型：0=其他 1=新增 2=修改 3=删除 |
| method | VARCHAR(200) | 调用的 Java 方法全路径 |
| request_method | VARCHAR(10) | HTTP 方法：GET/POST/PUT/DELETE |
| oper_url | VARCHAR(200) | 请求 URL |
| oper_name | VARCHAR(64) | 操作人用户名（从 SecurityContext 取） |
| oper_param | TEXT | 请求参数（JSON） |
| oper_result | TEXT | 返回结果（JSON） |
| status | TINYINT | 0=正常 1=异常 |
| error_msg | TEXT | 异常信息（正常时为空） |
| oper_ip | VARCHAR(128) | 操作者 IP |
| oper_time | DATETIME | 操作时间 |
| cost_time | BIGINT | 耗时（毫秒） |

> 这张表的 DDL 由 Claude 设计产出，你负责在 MySQL 执行。

---

## 三、实现步骤总览

### 第 1 步：数据库建表

`sys_operate_log`，字段见上方表格。

### 第 2 步：自定义注解 `@OperationLog`

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String title() default "";       // 模块标题，如 "用户管理"
    int businessType();               // 操作类型：1=新增 2=修改 3=删除
    boolean isSaveRequestData() default true;   // 是否保存请求参数
    boolean isSaveResponseData() default true;  // 是否保存返回结果
}
```

**为什么 businessType 用数字而不是枚举？**

数字存数据库更紧凑，前端展示时做映射（1→"新增"，2→"修改"）更灵活。你可以用 Java 枚举类定义常量方便代码里使用，但注解值和数据库存的都是数字。

### 第 3 步：实体类 + Mapper

`OperateLog` 实体类（对应 `sys_operate_log` 表），`OperateLogMapper extends BaseMapper<OperateLog>`。

### 第 4 步：AOP 切面 `OperationLogAspect`

这是本阶段的核心。切面要做的事：

```
1. 方法执行前：记录开始时间
2. 方法执行：joinPoint.proceed()
3. 方法执行后（无论成功还是异常）：
   - 从 SecurityContext 拿当前用户
   - 从 RequestContextHolder 拿请求信息（URL、HTTP方法、IP）
   - 组装 OperateLog 对象
   - 写入数据库
```

关键设计决策：

#### 3a. 日志写入不能影响主业务

如果日志表插入失败，**不应该**导致业务事务回滚。有两种做法：

| 方案 | 原理 | 优缺点 |
|------|------|--------|
| `@Async` 异步 | 日志在另一个线程写入 | 简单，但主线程结束时异步线程可能还没写完（应用关闭时丢日志） |
| 新事务（`REQUIRES_NEW`） | 日志在独立事务中提交 | 可靠，即使主事务回滚日志也会入库 |
| MQ 异步（阶段 7） | 发消息到队列，消费者写库 | 最可靠，但本阶段还没学 MQ |

**本阶段用 `@Async` 方案**（最简单），阶段 7 学完 MQ 后可以升级。

> 注意：`@Async` 需要在启动类上加 `@EnableAsync`。异步方法要在不同的类中调用（Spring AOP 代理的限制），所以通常抽一个 `OperateLogService` 来做写入。

#### 3b. 怎么拿到当前用户？

Spring Security 已经在 JwtFilter 里设置了 `SecurityContext`：

```java
SecurityContextHolder.getContext().getAuthentication().getName()
```

就能拿到当前用户名。

#### 3c. 怎么拿请求信息？

通过 `RequestContextHolder`（Spring MVC 自动注册）：

```java
ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
HttpServletRequest request = attrs.getRequest();
request.getRequestURL()    // URL
request.getMethod()         // GET/POST/...
request.getRemoteAddr()     // IP
```

#### 3d. 怎么序列化参数？

用 Jackson 的 `ObjectMapper` 把 `joinPoint.getArgs()` 转成 JSON 字符串存入 `oper_param`。

**注意：敏感字段脱敏**——请求参数可能包含密码，入库前应该把 `password` 字段过滤掉或替换为 `******`。

#### 3e. 耗时计算

```java
long startTime = System.currentTimeMillis();
// ... proceed ...
long costTime = System.currentTimeMillis() - startTime;
```

### 第 5 步：在 Controller 方法上加注解

例如：

```java
@PostMapping("/user/add")
@OperationLog(title = "用户管理", businessType = 1)
public Result<Void> add(@Validated UserDTO userDTO) { ... }

@DeleteMapping("/user/delete/{id}")
@OperationLog(title = "用户管理", businessType = 3)
public Result<Void> delete(@PathVariable Long id) { ... }
```

### 第 6 步：Knife4j 接口文档

Knife4j 是 Swagger/OpenAPI 的增强版，能自动根据你的 Controller 生成接口文档页面，支持在线测试。

**依赖**（springdoc-openapi + knife4j，Spring Boot 3.x 用法）：

```xml
<!-- pom.xml -->
<dependency>
    <groupId>com.github.xiaoymin</groupId>
    <artifactId>knife4j-openapi3-jakarta-spring-boot-starter</artifactId>
    <version>4.4.0</version>
</dependency>
```

**配置**（`application.yml`）：

```yaml
springdoc:
  swagger-ui:
    path: /swagger-ui.html
  api-docs:
    path: /v3/api-docs

knife4j:
  enable: true
  setting:
    language: zh_cn
```

**访问路径**：启动应用后访问 `http://localhost:端口号/doc.html`

**接口分组**：在 Controller 上加 `@Tag(name = "用户管理")`，在方法上加 `@Operation(summary = "新增用户")`，Knife4j 会自动按 Tag 分组。

---

## 四、文件清单（你需要在项目里创建的）

| 序号 | 文件 | 位置 | 说明 |
|------|------|------|------|
| 1 | `sys_operate_log` DDL | `resources/sql/` | 日志表建表语句 |
| 2 | `@OperationLog` | `common/annotation/` | 操作日志注解 |
| 3 | `OperateLog` | `entity/` | 日志实体类 |
| 4 | `OperateLogMapper` | `mapper/` | BaseMapper |
| 5 | `OperateLogService` + `Impl` | `service/` | 日志写入（@Async 异步） |
| 6 | `OperationLogAspect` | `common/aspect/` | AOP 切面（核心） |
| 7 | `DemoApplication` | 修改 | 加 `@EnableAsync` |
| 8 | `pom.xml` | 修改 | 加 knife4j 依赖 |
| 9 | `application.yml` | 修改 | 加 springdoc + knife4j 配置 |
| 10 | Controller 方法 | 修改 | 加 `@OperationLog` + `@Tag`/`@Operation` |

---

## 五、注意事项

1. **切面执行顺序**：如果同一个方法同时有 `@PreventDuplicate` 和 `@OperationLog`，注意执行顺序——通常防重复在外层（先拦截），日志在内层（先记录）。可以用 `@Order` 控制优先级（数字越小优先级越高）。

2. **参数序列化异常**：`joinPoint.getArgs()` 里可能包含无法序列化的对象（如 `HttpServletRequest`），需要过滤或 try-catch。

3. **日志查询接口**：后续可以做一个 `GET /operate-log/page` 分页查询接口，但不在本阶段强制要求，先把 AOP 记录链路跑通。

---

## 六、验收标准

1. 在 `UserController` 的增删改方法上加 `@OperationLog`
2. 通过 Postman 调用这些接口后，`sys_operate_log` 表有对应的记录
3. 记录中的操作人、请求参数、返回结果、耗时、IP 等字段正确填充
4. 访问 `/doc.html` 能看到 Knife4j 接口文档页面，接口按分组展示
5. 异常场景（如删除不存在的用户），日志的 `status` 为 1，`error_msg` 有异常信息

---

准备好了就开始，DDL 我来出，代码你来写。
