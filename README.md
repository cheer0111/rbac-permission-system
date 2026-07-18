# RBAC Permission Management System

基于 Spring Boot 3.x 的企业级后台权限管理系统 Demo，围绕 RBAC（用户-角色-菜单/权限）模型，串联起认证授权、缓存、分布式锁、AOP 等企业级后端核心技术点。

## 技术栈

| 层次 | 技术选型 |
|---|---|
| 基础框架 | Spring Boot 3.x |
| 持久层 | MyBatis-Plus |
| 认证授权 | Spring Security + JWT (jjwt 0.12.6) |
| 缓存 / 分布式锁 | Redis + Redisson |
| 权限模型 | RBAC（用户-角色-菜单，多对多） |
| 部署 | Docker |

## 已实现功能

- **JWT 认证**：`JwtUtil` 生成/校验 Token，自定义 `JwtAuthenticationFilter extends OncePerRequestFilter` 接入 Security 过滤器链
- **Spring Security 配置**：CSRF 关闭、无状态 Session、`@EnableMethodSecurity` 方法级权限控制
- **RBAC 权限模型**：用户-角色-菜单三级关联，菜单树通过 Stream `groupingBy` + 递归构建
- **MyBatis-Plus 进阶**：`@TableLogic` 逻辑删除、`MyMetaObjectHandler` 自动填充、`LambdaQueryWrapper` 分页查询
- **Redis 缓存**：权限数据 Cache-Aside 模式，`StringRedisTemplate` + 手动 `TypeReference` 反序列化，主动失效策略
- **防重复提交**：基于 AOP + Redisson `RLock` 实现，采用"冷却期内不主动解锁"策略防止用户重复点击/重放请求

## 项目结构

```
demo/
├── src/main/java/cheer/
│   ├── common/     # 通用组件（统一返回体、全局异常、AOP 切面等）
│   └── demo/       # 业务模块
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

> 需要预先启动 MySQL 和 Redis，并在 `application.yml` 中配置好连接信息。

## 关于这个项目

这是一个持续迭代中的个人学习/练手项目，用于系统性掌握企业级 Spring Boot 后端开发。开发过程采用人机协作模式：由我主导技术方向和架构决策，AI 协助编码实现与代码审查，我负责理解、验证并整合最终产出。

如果你也想按照类似的路径系统学习企业级 Spring Boot 开发（认证授权 → 权限模型 → 缓存/分布式锁 → 日志 → 消息队列 → 容器化部署），可以参考这份分阶段学习计划的思路：以 RBAC 权限管理系统作为载体项目，逐步串联 MyBatis-Plus、Spring Security、JWT、Redis/Redisson 等技术栈，每个阶段设定明确的里程碑和常见踩坑点，适合已掌握 Spring Boot 基础 CRUD 和 IoC/AOP 概念、希望进一步整合企业级技术栈的学习者。

## License

MIT
