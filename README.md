# RBAC Permission Management System

基于 Spring Boot 3.5 的企业级后台权限管理系统，完整实现 RBAC（基于角色的访问控制）模型，覆盖认证授权、缓存、分布式锁、消息队列、AOP、定时任务、文件存储等企业级后端核心技术栈。

## 技术栈

| 层次 | 技术选型 | 版本 |
|---|---|---|
| 语言 | Java | JDK 17+ |
| 基础框架 | Spring Boot | 3.5.x |
| 持久层 | MyBatis-Plus | 3.5.10 |
| 数据库 | MySQL | 8.0+ |
| 连接池 | Druid | 1.2.23 |
| 认证授权 | Spring Security + JWT (jjwt) | 6.x / 0.12.6 |
| 缓存 / 分布式锁 | Redis + Redisson | 3.27.0 |
| 消息队列 | RabbitMQ (Spring AMQP) | 3.13 |
| 接口文档 | Knife4j (OpenAPI 3) | 4.4.0 |
| 部署 | Docker | — |

## 已实现功能

**认证授权**
- JWT 无状态认证：`JwtUtil` 生成/解析 Token（HMAC-SHA256 签名），自定义 `JwtAuthenticationFilter extends OncePerRequestFilter` 接入 Security 过滤器链
- Spring Security 配置：CSRF 关闭、STATELESS 无状态 Session、路径白名单、`@PreAuthorize` 方法级权限控制（hasAuthority）
- BCrypt 密码加密（注册加密 + 登录校验）

**RBAC 权限模型**
- 核心五表设计（`sys_user` / `sys_role` / `sys_menu` / `sys_user_role` / `sys_role_menu`），外加操作日志、通知日志等业务扩展表
- 菜单树形结构：自引用 `parent_id` + Stream `groupingBy` 分组、递归构建
- 雪花算法主键（`@TableId(type = IdType.ASSIGN_ID)`）

**持久层（MyBatis-Plus）**
- `LambdaQueryWrapper` 条件构造（eq/like/in/between/orderBy）+ 分页插件
- `@TableLogic` 逻辑删除、`MetaObjectHandler` 自动填充（创建/更新时间）
- Druid 连接池 + SQL 监控面板（`/druid`）、慢查询统计

**缓存与分布式锁**
- Redis 缓存读穿透模式（Cache Miss → 查库 → 回写缓存，TTL 30min），异常自动降级不影响主业务
- `StringRedisTemplate` + 手动 `TypeReference` 反序列化
- Redisson `RLock` 分布式锁实现防重复提交：`@PreventDuplicate` 自定义注解 + SpEL 动态解析锁 Key，冷却期内不主动解锁

**消息队列**
- RabbitMQ 声明式配置（DirectExchange + Queue + Binding），`RabbitTemplate` 生产者 + `@RabbitListener` 消费者
- 用户注册成功后异步发送欢迎通知，业务与通知解耦
- Jackson2JsonMessageConverter JSON 消息序列化，发布确认（publisher-confirm）与返回（publisher-returns）

**定时任务 & 文件存储**
- `@Scheduled` + Cron 表达式：定时清理过期通知日志、定时刷新 Redis 菜单缓存
- 文件上传：`MultipartFile` 日期目录 + UUID 文件名防冲突，静态资源映射（`/uploads/**`）

**工程规范**
- 统一响应封装 `Result<T>` + `@RestControllerAdvice` 全局异常处理 + 自定义业务异常
- AOP 操作日志：自定义 `@OperationLog` 注解 + 环绕通知，`@Async` 异步落库，敏感字段（密码）脱敏
- Knife4j/OpenAPI 3 接口文档自动生成

## 项目结构

```
demo/
├── src/main/java/cheer/
│   ├── common/     # 通用组件（统一返回体、全局异常、AOP 切面、Redis/Redisson 配置等）
│   └── demo/       # 业务模块（用户、角色、菜单、认证、日志等）
├── src/main/resources/
├── Dockerfile
└── pom.xml
```

## 快速启动

### 方式一：本地运行

```bash
mvn clean package
java -jar target/demo-0.0.1-SNAPSHOT.jar
```

### 方式二：Docker

```bash
docker build -t demo:latest .
docker run -p 8080:8080 demo:latest
```

> 需要预先启动 MySQL、Redis、RabbitMQ，并在 `application.yml` 中配置好连接信息。

启动后访问 `http://localhost:8080/doc.html` 查看接口文档。

## Roadmap

- [x] 阶段 1-6：工程规范化 / MyBatis-Plus 进阶 / RBAC 模型 / JWT 认证授权 / Redis 缓存与分布式锁 / AOP 日志
- [x] 阶段 7：RabbitMQ 异步消息
- [x] 阶段 8：定时任务 + 文件存储
- [ ] 阶段 9：Docker Compose 全环境编排 + Nginx 反向代理
- [ ] 阶段 10（可选）：Spring Cloud Alibaba 微服务化（Gateway / Nacos / Sentinel / Seata）

## 关于这个项目

这是一个持续迭代中的个人学习/练手项目，用于系统性掌握企业级 Spring Boot 后端开发。开发过程采用人机协作模式：由我主导技术方向和架构决策，AI 协助编码实现与代码审查，我负责理解、验证并整合最终产出。

如果你也想按照类似的路径系统学习企业级 Spring Boot 开发（工程规范化 → 持久层进阶 → 权限模型 → 认证授权 → 缓存/分布式锁 → 日志与文档 → 消息队列 → 定时任务与文件存储 → 容器化部署 → 微服务化），可以参考这份分阶段学习计划的思路：以 RBAC 权限管理系统作为载体项目，逐步串联 MyBatis-Plus、Spring Security、JWT、Redis/Redisson、RabbitMQ 等技术栈，每个阶段设定明确的里程碑和常见踩坑点，适合已掌握 Spring Boot 基础 CRUD 和 IoC/AOP 概念、希望进一步整合企业级技术栈的学习者。

## License

MIT
