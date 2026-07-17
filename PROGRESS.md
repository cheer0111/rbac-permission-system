# 项目进度追踪

> 本文件是当前进度的唯一权威来源。每次任务完成或推进后都会更新。
> 新开会话时，先读这个文件 + `springboot-enterprise-demo-plan.md`，再决定下一步该做什么，不要凭空重新规划。

## 协作模式（重要，勿破坏）

- 项目脚手架/业务代码（entity/controller/service/mapper 等）由用户自己写，Claude 不代写。
- **数据库设计（表结构/DDL/ER关系）由 Claude 负责设计并产出**，用户负责在自己的 MySQL 里执行、验证。这是 2026-07-06 用户明确要求的分工调整。
- Claude 的角色：架构/设计类工作（如数据库设计）直接产出交付物；代码实现类工作布置任务（含验收标准）→ 用户实现 → Claude review（指出问题，不直接改代码）→ 确认通过 → 更新本文件 → 布置下一个任务。
- 不用逐个概念提问阻塞流程；有必要讲的原理简要自问自答带过即可。

## 阶段总览（对应 plan 文件第 3 节的 10 个阶段）

| 阶段 | 内容 | 状态 |
|---|---|---|
| 1 | 工程规范化（Result/全局异常/参数校验） | ✅ 已完成 |
| 2 | MyBatis-Plus + Druid | ✅ 已完成 |
| 3 | RBAC 权限模型设计 | ✅ 已完成 |
| 4 | Spring Security + JWT | ✅ 已完成 |
| 5 | Redis 缓存 + Redisson 分布式锁 | ✅ 已完成 |
| 6 | AOP 操作日志 + Knife4j | ✅ 已完成 |
| 7 | RabbitMQ 异步消息 | ⬜ 未开始 |
| 8 | 定时任务 + 文件存储 | ⬜ 未开始 |
| 9 | Docker 容器化部署 | ⬜ 未开始 |
| 10 | （可选）Spring Cloud Alibaba 微服务化 | ⬜ 未开始 |

## 已确认的技术决策

- 技术栈：Spring Boot 3.4.x，JDK 17+，Maven，MyBatis-Plus（非 JPA），Druid 连接池
- 前端：不做前端页面，只用 Knife4j/Postman 测接口
- 包结构：按技术层分包（layer-first，对照若依风格），基础包名待用户初始化项目时自定
- 开发环境：用户用 IDEA 开发，IDE 自带 JDK17+/Maven，本机命令行 Java 版本较旧（1.8），**不用命令行编译验证，以 IDEA 内运行结果为准**

## 数据库设计约定（阶段 3）

- 核心表：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`
- 业务表通用字段：`create_time`、`update_time`、`del_flag`（逻辑删除，0=正常 1=已删除）
- 中间表（`sys_user_role`/`sys_role_menu`）不需要通用字段，只需要外键 + 主键
- `sys_menu` 需支持树形结构（自引用外键），并用一个字段区分 目录/菜单/按钮 三种类型
- 主键策略、字段类型细节：由用户自行设计并给出理由（分布式 ID vs 自增 ID 的取舍）

## 当前正在进行的任务

**任务 1｜初始化项目骨架**（项目名 `demo`，包名 `cheer.demo`）—— ✅ 依赖问题已全部修复，待用户 IDEA 启动验证
- 已具备：JDK17、Spring Boot 3.5.16、Web/Validation/Lombok、mybatis-plus-spring-boot3-starter 3.5.10.1
- 实测 `pom.xml`：
  1. ✅ 已含 `mysql-connector-j`（原缺 MySQL 驱动问题已解决）
  2. ✅ 已换为 `druid-spring-boot-3-starter` 1.2.23（原 1.2.1 不兼容 Spring Boot 3 问题已解决）
  3. ✅ `application.yml` 已补 `spring.datasource`（driver-class-name + druid.url/username/password），指向 `rbac_demo` 库
- `DemoApplication` 已加 `@MapperScan("cheer.demo.mapper")`，Mapper 扫描无误
- 状态：结构层面 review 通过；待用户 IDEA 启动应用确认能正常起来（首次启动应验证数据源能连上 `rbac_demo`，前提是 schema.sql 已执行）

**数据库设计** —— ✅ 已完成（Claude 产出）
- 文件：`demo/src/main/resources/sql/schema.sql`
- 5 张表：`sys_user`/`sys_role`/`sys_menu`/`sys_user_role`/`sys_role_menu`，主键用雪花算法 BIGINT，通用字段 create_time/update_time/del_flag，menu 表自引用树形结构
- 待用户动作：在自己的 MySQL 里执行这个脚本，确认无误后回报

**任务 3｜工程规范化（阶段1）** —— 🟢 前3项已完成（Result/ResultCode、BusinessException、全局异常处理器）
- 第4项（验证用测试接口）用户选择不写抛弃式 TestController，改为直接实现真实的"新增用户"功能来验证 —— 见下方任务3.4

**任务 3.4｜新增用户功能（合并阶段1验证 + 提前引入阶段2 MyBatis-Plus 基础）** —— ✅ 已完成（代码 + 运行时验收均通过，2026-07-07 二次复核）
- 实际文件名：`User`(entity)/`UserDTO`/`UserMapper`/`UserService`+`UserServiceImpl`/`UserController`，接口路径 `POST /user/add`
- 已修复：`User.id` 改为 `Long` + `@TableId(type = IdType.ASSIGN_ID)`；`UserMapper extends BaseMapper<User>` + `@Mapper` + 全局 `@MapperScan`
- **设计决策（非bug，已确认）**：走**表单提交**（`x-www-form-urlencoded`）不加 `@RequestBody`，走 ModelAttribute 绑定。
- `UserDTO`：`username`/`password`/`nickname` 均有 `@NotBlank`，`email` 有 `@Email`；`UserServiceImpl` 有 `selectCount` 查重抛 `BusinessException`
- 运行时验收（用户 Postman 验证）：新增入库 / username 空触发校验 / username 重复触发异常 三条分支均通过

**任务 4｜MyBatis-Plus 实操（阶段2）** —— ✅ 已完成（代码 + 运行时验收均通过，2026-07-07 二次复核）
> 前置条件已满足：用户在 MySQL 执行了 `schema.sql`（建 `rbac_demo` 库 + 5 张表）；Postman 测试通过。

实现清单（全部由用户实现，Claude 出设计 + review）：
1. ✅ 分页插件：`MybatisPlusConfig` 注册 `MybatisPlusInterceptor` + `PaginationInnerInterceptor(DbType.MYSQL)`（`@Configuration`+`@Bean`）
2. ✅ 用户列表分页接口：`GET /user/page` → `userService.query` → `selectPage`，`username`(like)/`status`(eq) 条件拼接正确
3. ✅ 逻辑删除：`User.delFlag` 加 `@TableLogic`；`DELETE /user/delete/{id}` → `deleteById`（生成 UPDATE 而非 DELETE）
4. ✅ 自动填充：`MyMetaObjectHandler` `@Component` 实现 `insertFill`/`updateFill`；实体 `createTime`/`updateTime` 配 `@TableField(fill=...)`，`add()` 手动 set 已移除
5. ✅ Druid 监控页：`application.yml` 配 `filters: stat` + `stat-view-servlet` + `web-stat-filter`，`/druid` 可访问

**复核确认的两处小修（用户已改）**
- `UserServiceImpl.add`：`setAvatar(null)` 替代原占位字符串 `"www.baidu.com"`；`setDelFlag(0)` 加固逻辑删除默认值（避免 null 入库风险）

**可选进阶（不做不阻塞）**：`@Version` 乐观锁——需先 `ALTER TABLE sys_user ADD COLUMN version BIGINT NOT NULL DEFAULT 0`，实体加 `@Version private Long version`；适合后面做并发更新防覆盖。

**任务 5｜菜单树查询 + 动态菜单接口（阶段3）** —— ✅ 已完成（接口A+B 均代码+Postman 验收通过）

**接口A（全量菜单树 GET /menu/tree）** ✅
- Postman 验证：树形 JSON 嵌套正确（系统管理→用户管理→用户新增），层级无误

**接口B（动态菜单树 GET /menu/user-tree?userId=）** ✅
- 四步链路：userId→roleIds→menuIds→菜单列表→buildTree
- `getRoleIdsByUserId(userId)`：eq+map+collect 提取 roleId 列表
- `getMenuIdsByRoleIds(roleIds)`：in+map+collect 提取 menuId 列表
- `userTree(userId)`：串四步+空值防御（roleIds/menuIds 为空时直接返回空列表）
- Postman 验证：userId=1(admin)返回全部菜单；userId=2(common)只返回用户管理分支，角色管理和首页不出现 ✅

**踩坑修复记录（本次会话）**
- `UserRole`/`RoleMenu` 的 `@TableId` 放错位置（联合主键表不应有单字段 @TableId）→ 已删
- `Long` 用 `==` 比引用不比值 → 改为 `.equals()`
- `.in()` 传 Long 而非 Collection → 入参改为 `List<Long>`

**协作模式：** 导师+结对编程（vibe coding），Claude 逐步引导不直接给代码

**产出文档**
- `d:\mySpringboot\menu-tree-guide.md` — 菜单树构建完整指南（10章）
- `d:\mySpringboot\preview-dynamic-menu.md` — 预习：动态菜单树 + LambdaQueryWrapper 进阶
     4. `buildTree` → 返回该用户可见菜单树。
   - 验收：换不同 `userId`（不同角色）返回不同菜单树；无权限的菜单不出现。
   - 说明：真正的"登录后自动取当前用户"要等阶段4（Security 上下文），这里先用 `userId` 参数模拟，取数逻辑与将来一致。

5. **（可选，不阻塞）角色管理基础**：`Role` 增删改查 + 给角色分配菜单（写 `sys_role_menu`）。可作为 Task 5 延伸。

**整体验收（任务5）**
- `GET /menu/tree` 返回正确多级菜单树（children 嵌套、sort 有序）；
- `GET /menu/user-tree?userId=` 按角色返回对应菜单树，角色不同结果不同；
- 逻辑删除的菜单不出现在树中（`@TableLogic` 生效）。

**样例数据（Claude 设计交付物，供 Postman 联调，需用户在自己 MySQL 执行）**
见对话内联 SQL；执行后即可测 `tree` / `user-tree`。建议：1 个管理员角色(admin)挂全部菜单，1 个普通角色(common)只挂部分菜单，再用两个不同 userId 对比 `user-tree` 结果。

**任务 6｜Spring Security + JWT（阶段4）** —— ✅ 已完成（2026-07-09）

实现清单：
1. ✅ pom.xml 加依赖：spring-boot-starter-security、jjwt-api/impl/jackson 0.12.6
2. ✅ JwtUtil：generateToken（userId/username/permissions）+ parseToken
3. ✅ SecurityConfig：csrf 禁用、STATELESS、注册 JwtFilter、路径白名单（/auth/login、/druid/**）、其余 authenticated
4. ✅ JwtAuthenticationFilter：OncePerRequestFilter，解析 token → 设置 SecurityContext，无 token/无效 token 安静放行
5. ✅ 登录接口：LoginDTO + AuthService/AuthServiceImpl + AuthController（POST /auth/login）
6. ✅ @PreAuthorize 方法级权限控制：UserController 每个 API 对应独立权限标识
7. ✅ 数据修复：sys_user_role 的 user_id 改为正确的雪花 ID（原 1/2 不匹配 sys_user 表）

**RBAC 权限链路完整打通：**
```
登录 → 查 user → 查 roleIds → 查 menuIds → 提取 perms → 生成 token
请求 → JwtFilter 解析 token → SecurityContext → @PreAuthorize 校验 → 放行/403
```

**数据库：** 完整初始化脚本 `rbac_demo.sql`（库名 rbac），含 5 表 + 测试数据 + 菜单按钮拆分

**踩坑修复记录（本次会话）**
- JwtFilter 里 throw BusinessException → 改为安静放行（Filter 不做权限拒绝，交给 Security）
- "Bearer" vs "Bearer "（少空格匹配到错误 token）
- JwtFilter catch 块缺少 filterChain.doFilter → 请求挂住
- sys_user_role 假 ID（1/2）不匹配雪花 ID → 全部修正

**产出文档**
- `d:\mySpringboot\spring-security-jwt-guide.md` — Security+JWT 完整指南（7章）
- `d:\mySpringboot\rbac-model-design-report.md` — RBAC 权限模型设计报告（11章）
- `d:\mySpringboot\rbac-implementation-guide.md` — RBAC 代码实现指南（6章）

**任务 7｜Redis 缓存 + Redisson 分布式锁（阶段5）** —— ✅ 已完成（2026-07-14）

实现清单：
1. ✅ pom.xml 加依赖：spring-boot-starter-data-redis、commons-pool2、redisson-spring-boot-starter 3.27.0
2. ✅ application.yml 配置 spring.data.redis（Lettuce 连接池）
3. ✅ RedisConfig：自定义 RedisTemplate<String, Object>（Jackson 序列化、JavaTimeModule）
4. ✅ MenuServiceImpl Redis 缓存：menu:tree:{all}、user:menuTree:{userId}、user:perms:{userId}（30min TTL，读穿透+降级）
5. ✅ @PreventDuplicate 自定义注解：key / expireSeconds(默认5) / message(默认"操作太频繁，请勿重复提交")
6. ✅ PreventDuplicateAspect：RLock 分布式锁（tryLock waitTime=0 + leaseTime 冷却期）、SpEL 解析 key、成功不释放/失败释放/try-catch 保证安全

**设计决策（非bug，已确认）**
- 成功时不 unlock，让锁靠 leaseTime 自然过期作为冷却期，防止冷却期内重复请求
- 失败时主动 unlock，允许用户立即重试而非白等冷却期
- StandardEvaluationContext 用于 SpEL 解析（学习项目，表达式由开发者自己写，安全风险可接受）

**任务 8｜AOP 操作日志 + Knife4j（阶段6）** —— ✅ 已完成（2026-07-17）

实现清单：
1. ✅ `sys_operate_log` 建表（DDL 由 Claude 产出，用户执行）
2. ✅ `@OperationLog` 自定义注解（title/businessType/isSaveRequestData/isSaveResponseData）
3. ✅ `OperateLog` 实体 + `OperateLogMapper`（BaseMapper<OperateLog>）
4. ✅ `OperateLogService` + `OperateLogServiceImpl`（@Async 异步写入）
5. ✅ `OperationLogAspect`：@Around 拦截 → SecurityContext 取操作人 → RequestContextHolder 取请求信息 → Jackson 序列化参数（password 脱敏）+ 返回结果 → 异步落库 → 异常不吞
6. ✅ DemoApplication 加 `@EnableAsync`
7. ✅ Knife4j：knife4j-openapi3-jakarta-spring-boot-starter 4.4.0 + springdoc/knife4j yml 配置
8. ✅ SecurityConfig 白名单加 `/doc.html`、`/swagger-ui/**`、`/v3/api-docs/**`、`/webjars/**`
9. ✅ UserController 的 addUser/deleteById 加 `@OperationLog`

**踩坑修复记录（本次会话）**
- OperateLogMapper 泛型写成 `BaseMapper<OperateLogMapper>`（指向自己）→ 改为 `BaseMapper<OperateLog>`
- MethodSignature 用了 MyBatis 的 `MapperMethod.MethodSignature` → 改为 AspectJ 的 `org.aspectj.lang.reflect.MethodSignature`
- `@Around` 注解漏加、方法缺少 `public`、异常被吞未重新抛出
- application.yml knife4j 缩进错误（嵌套在 springdoc 下）→ 移为顶级 key
- ObjectMapper 未注册 JavaTimeModule 导致 LocalDateTime 序列化失败 → 加 `registerModule(new JavaTimeModule())`

## 协作节奏（2026-07-06 起）

用户要求：不用每完成一小步就来确认，自己按节奏推进，卡住了才会主动来问。Claude 不用等用户逐一回报任务1/2的完成情况就可以继续布置下一个任务；但仍然是"用户写代码、Claude 出设计/审查"的分工不变。

## 下一步

1. **阶段7：RabbitMQ 异步消息**
2. 之后顺序：阶段8（定时任务 + 文件存储）→ 阶段9（Docker 容器化部署）→ ...

## 变更记录

- 2026-07-17：任务8（阶段6）AOP 操作日志 + Knife4j 验收通过——@OperationLog 注解 + OperationLogAspect 切面（@Async 异步落库、password 脱敏、JavaTimeModule 序列化）+ Knife4j 文档页（/doc.html 可访问）。**阶段6 完成，前六个阶段全部关闭。**
- 2026-07-14：任务7（阶段5）PreventDuplicateAspect review 通过——RLock tryLock(waitTime=0)+leaseTime 冷却期、SpEL key 解析、成功不释放/失败释放。**阶段5 完成，前五个阶段全部关闭。**

- 2026-07-12：确认阶段5进度——Redis 缓存部分（依赖/配置/RedisConfig/MenuServiceImpl 缓存）已完成；Redisson 分布式锁部分进行中（@PreventDuplicate 注解已完成，PreventDuplicateAspect 切面写了一半，待补锁逻辑 + parseSpelKey + try/finally）。**阶段5 状态：进行中。**
- 2026-07-06：确立协作模式（用户写代码，Claude 布置任务+review）；完成架构拆解讲解；布置任务1（项目骨架）和任务2（数据库设计）；创建本进度文件
- 2026-07-06：用户反馈"数据库设计应该是你的任务"，调整分工——数据库设计改为 Claude 直接产出。Review 了用户提交的项目骨架（发现2个依赖问题，退回修复），Claude 产出 5 张表 DDL（schema.sql）
- 2026-07-07：任务1 依赖问题 + 数据源配置修复；任务3.4 修复（@NotBlank 移到字段、补查重）；下达任务4（阶段2 MyBatis-Plus 实操）
- 2026-07-07（二次验收）：任务3.4 + 任务4 均代码+运行时验收通过，阶段1、阶段2 完成
- 2026-07-07：下达任务5（阶段3）；用户切换为导师/结对编程模式（vibe coding）；产出 menu-tree-guide.md + preview-dynamic-menu.md
- 2026-07-08：任务5 接口A（全量菜单树）完成；修 Long== vs .equals()、@TableId 误放中间表等坑
- 2026-07-09：任务6（阶段4 Spring Security + JWT）完成——登录接口、JwtFilter、SecurityConfig、@PreAuthorize 权限控制全部打通，RBAC 认证授权链路完整验收通过 ✅。**阶段4 完成，前四个阶段全部关闭。**
