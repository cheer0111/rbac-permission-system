# SpringBoot 企业级项目学习计划

> 面向读者：已经能独立完成 SpringBoot 基础 CRUD（Controller → Service → Mapper/Repository 三层跑通）的学习者。IoC/DI/AOP、Bean 生命周期、事务管理这些 Spring 核心概念、以及 Servlet 和 Spring MVC 的关系，也已经过了一遍。
>
> 这份计划不重复讲这些基础，而是直接进入"怎么把一堆技术整合成一个真正像样的企业级项目"。

---

## 0. 先明确几个假设

这些假设决定了下面整个计划的走向，如果和你的实际情况不符，可以按需调整。

**① 载体项目：企业级后台权限管理系统（RBAC）**
国内自学/面试练手项目里最经典的一类，能自然串起认证、权限、缓存、日志、异步这几乎全部企业级技术点。若依（RuoYi）就是这类项目的标杆开源实现，后面会用作对照参考。

> 如果你更想练"业务并发"而不是"权限体系"，可以把载体换成小型电商系统（商品-购物车-订单-库存扣减），本计划第 3、4 阶段的业务模块换掉即可，其余阶段完全通用。

**② 技术版本：主线用 Spring Boot 3.x 最新稳定版（3.4/3.5），JDK 17 起步，建议直接上 21**
这里有必要说一句现状：Spring Boot 目前的官方主线其实已经是 **4.x** 了——4.0 于 2025 年 11 月发布，构建在 Jakarta EE 11、Jackson 3 之上；今年 6 月 10 日又发布了 4.1，上一代 3.5 分支已经在 6 月 30 日正式停止开源维护。若依这类经典参考项目主线也已经跟进升级到了 Spring Boot 4。

不直接选 4.x 的原因：这份计划要一口气整合近十项技术，先把"版本兼容性踩坑"的成本降到最低，比追最新版本更重要，而 3.x 的教程、博客、StackOverflow 答案密度目前仍是最高的。等你把整套技术栈都跑通了，再迁移到 4.x 会轻松很多（届时可以直接对照若依的新分支）。如果你更想对齐"最新"，直接用 4.x 也完全可以，整个计划的阶段划分不变，只是遇到教程对不上号的地方要多啃官方文档。

**③ 持久层选 MyBatis-Plus，而不是 Spring Data JPA**
不是 JPA 不好，而是国内企业招聘和绝大多数中文教程、开源项目走的都是 MyBatis-Plus 技术栈，练这个更贴近你以后会遇到的真实代码。

**④ 前端怎么办**
两种都可以：(a) 只用 Knife4j／Postman 测接口，把精力全部放在后端整合上；(b) 找一个开源的 Vue3 + Element Plus 极简后台模板对接一下，感受真实的前后端联调（你已经有 Node.js/npm 环境，跑起来很轻松）。看你更想练后端整合，还是也想练一下前后端协作。

**⑤ 节奏：每周 10-15 小时，全程 8-10 周**
阶段顺序比时间长短更重要，可以按自己的节奏压缩或拉长。

---

## 1. 技术全景图

```
浏览器 / Postman / Knife4j 文档页
        │  HTTP 请求，携带 JWT
        ▼
Controller 层     统一返回体 Result<T> ／ 参数校验 ／ 全局异常处理
        │
        ▼
Service 业务层    @PreAuthorize 权限校验 ／ AOP 操作日志 ／ @Cacheable 缓存
        │
        ├──────────────┬───────────────┐
        ▼              ▼               ▼
MyBatis-Plus       Redis            RabbitMQ
+ Druid 连接池     缓存/分布式锁/     异步通知
        │          Token黑名单       削峰解耦
        ▼
      MySQL
```

| 层次 | 技术选型 | 解决的问题 |
|---|---|---|
| 工程规范 | 统一返回体 + 全局异常 + 参数校验 | 让接口"长得像"企业项目 |
| 持久层 | MyBatis-Plus + Druid | 少写模板代码、SQL 监控 |
| 权限模型 | RBAC（用户-角色-菜单/权限） | 谁能访问什么 |
| 认证授权 | Spring Security + JWT | 前后端分离下"你是谁、能干嘛" |
| 缓存 | Redis + Redisson | 提速、限流、分布式锁 |
| 日志 | 自定义注解 + AOP | 谁在什么时候做了什么 |
| 接口文档 | Knife4j（基于 springdoc-openapi） | 前后端联调不靠嘴说 |
| 异步消息 | RabbitMQ | 削峰、解耦、异步通知 |
| 定时任务 | @Scheduled（进阶 XXL-JOB） | 报表、清理、超时处理 |
| 文件存储 | MinIO / 本地存储 | 头像、附件上传 |
| 部署 | Docker + Docker Compose | 一条命令起环境 |
| （进阶）微服务 | Spring Cloud Alibaba | 服务拆分、网关、限流熔断 |

---

## 2. Demo 项目模块清单

1. 登录认证（账号密码登录、图形验证码、Token 刷新）
2. 用户管理（增删改查、重置密码、分配角色）
3. 角色管理（角色-菜单权限分配）
4. 菜单/权限管理（树形结构，控制前端路由和按钮显隐）
5. 部门管理（可选，用来练"数据权限"）
6. 操作日志 / 登录日志
7. 个人中心（头像上传、修改密码）

---

## 3. 分阶段学习路线

### 阶段 1（第 1 周）｜工程规范化：让项目"看起来"像企业项目

**学什么**
- 统一响应结果封装 `Result<T>`
- 全局异常处理（`@RestControllerAdvice` + 自定义业务异常）
- 参数校验（`@Valid`/`@Validated`，分组校验）
- 分层规范：Controller 只做接收/返回，Service 做业务，Mapper 只做数据访问

**代码示例**
```java
@Data
public class Result<T> {
    private Integer code;
    private String message;
    private T data;

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(200);
        result.setMessage("success");
        result.setData(data);
        return result;
    }

    public static <T> Result<T> fail(int code, String message) {
        Result<T> result = new Result<>();
        result.setCode(code);
        result.setMessage(message);
        return result;
    }
}
```

**里程碑**：用户模块的增删改查全部改造成"统一返回 + 抛业务异常交给全局处理"的写法。

**踩坑提醒**
- 不要在 Controller 里写一堆 try-catch，异常应该往上抛，交给全局异常处理器统一处理
- 校验失败信息不要用"参数错误"这种泛化提示，把 `BindingResult` 里的字段信息提取出来返回，方便前端定位

---

### 阶段 2（第 1-2 周）｜持久层进阶：MyBatis-Plus + Druid

**学什么**
- MyBatis-Plus 的 CRUD 接口、条件构造器（Wrapper）
- 分页插件（`MybatisPlusInterceptor`）
- 逻辑删除（`@TableLogic`）
- 自动填充（创建时间/更新时间，`MetaObjectHandler`）
- 乐观锁（`@Version`）
- Druid 连接池 + 内置监控页面

**里程碑**：用户列表接口支持分页 + 条件查询，删除改为逻辑删除，打开 Druid 监控页能看到 SQL 执行情况。

**踩坑提醒**
- 分页插件必须注册为 Spring Bean，忘记注册的话 `Page` 对象查出来的还是全量数据
- 逻辑删除字段的数据库类型要和实体类字段类型对应，否则 `@TableLogic` 不生效

---

### 阶段 3（第 2-3 周）｜RBAC 权限模型设计

**学什么**
- RBAC 模型：用户-角色（多对多）、角色-菜单/权限（多对多）
- 表设计：`sys_user`、`sys_role`、`sys_menu`、`sys_user_role`、`sys_role_menu`
- 菜单树形结构的构建（递归或 Stream 分组）
- 动态菜单：登录后返回当前用户能看到的菜单树，前端据此做动态路由

**里程碑**：完成角色管理、菜单管理模块，实现"登录后返回当前用户菜单权限树"接口。

**踩坑提醒**
- 权限设计不要一开始就追求"字段级/数据行级"权限，先把菜单 + 按钮级权限做扎实，这个粒度已经能覆盖 90% 场景
- 多对多中间表尽量单独建实体，不要偷懒直接拼 SQL，后期想加字段（比如数据范围）会很痛苦

---

### 阶段 4（第 3-4 周）｜认证授权：Spring Security + JWT

**学什么**
- Spring Security 过滤器链原理（至少要清楚 `UsernamePasswordAuthenticationFilter` 和 `FilterChain` 的大致顺序）
- 自定义 `UserDetailsService`，从数据库加载用户和权限
- JWT 的生成、校验、刷新（推荐用 `jjwt` 或 Hutool 的 JWT 工具）
- 自定义 JWT 过滤器插入到 Security 过滤器链中
- 方法级权限控制（`@PreAuthorize("hasAuthority('user:add')")`）

**代码示例（JWT 过滤器骨架）**
```java
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String token = request.getHeader("Authorization");
        if (StringUtils.hasText(token) && jwtUtil.validateToken(token)) {
            Authentication authentication = jwtUtil.getAuthentication(token);
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        chain.doFilter(request, response);
    }
}
```

**里程碑**：未登录访问接口返回 401；登录后带 Token 能访问受保护接口；没有对应权限的接口返回 403。

**踩坑提醒**
- JWT 过滤器要注册在 `UsernamePasswordAuthenticationFilter` 之前，顺序错了认证不生效
- 密码入库必须用 `BCryptPasswordEncoder` 加密，千万不要明文或简单 MD5 存储
- Token 不要设置过长的有效期，配合"刷新 Token"机制，而不是发一个"永久令牌"
- 如果 Spring Security 的学习曲线让你觉得太陡，也可以先用更轻量的 Sa-Token 把认证授权跑通，后面再回头研究 Spring Security 的原理

---

### 阶段 5（第 4-5 周）｜缓存与分布式锁：Redis

**学什么**
- `RedisTemplate` 基本使用（String/Hash/List/Set/ZSet 的场景选型）
- Spring Cache 注解（`@Cacheable`/`@CacheEvict`）做方法级缓存
- 应用场景：验证码存 Redis（带过期时间）、登录 Token 黑名单管理、热点数据（菜单树/字典表）缓存
- Redisson 实现分布式锁（防止重复提交、库存扣减这类场景）
- 简单了解缓存穿透/击穿/雪崩的成因和常见解法（布隆过滤器、互斥锁、随机过期时间）

**里程碑**：登录验证码走 Redis；退出登录时把 Token 加入黑名单（而不是等它自然过期）；菜单树查询加上缓存。

**踩坑提醒**
- 缓存一定要设置过期时间，没有过期时间的 Key 积累起来迟早把 Redis 内存占满
- 分布式锁的"加锁 + 设置过期时间"必须是原子操作，用 Redisson 的 `RLock` 而不是自己拆成两条命令实现，否则容易出现锁没设置过期时间就宕机导致死锁

---

### 阶段 6（第 5-6 周）｜日志与接口文档

**学什么**
- 自定义注解 + AOP 环绕通知，记录"谁在什么时候做了什么操作"
- 操作日志异步写入（结合下一阶段的消息队列，或者先用 `@Async`），避免日志拖慢主业务
- Knife4j 集成（现在的 Knife4j 是搭在 springdoc-openapi 之上的，Swagger2 时代用的 Springfox 已经停更多年，Spring Boot 3 起官方也建议换成 springdoc），接口分组、在线测试

**代码示例（操作日志注解）**
```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OperationLog {
    String value(); // 操作描述，例如"新增用户"
}
```
对应的 Aspect 在方法执行后异步落库，不影响主业务流程。

**里程碑**：关键的增删改接口都能自动记录操作日志；打开 Knife4j 页面（默认路径 `/doc.html`）能看到分组清晰的接口文档并直接在线测试。

**踩坑提醒**
- 日志记录逻辑绝对不能影响主业务，比如日志表插入失败不应该导致整个业务事务回滚
- AOP 切点尽量精确到"带有 `@OperationLog` 注解的方法"，不要笼统地切所有 Controller 方法，否则性能损耗和日志噪音都会很大

---

### 阶段 7（第 6-7 周）｜异步消息：RabbitMQ

**学什么**
- RabbitMQ 核心概念（Exchange/Queue/Binding，交换机类型 Direct/Topic/Fanout）
- Spring AMQP 整合，`@RabbitListener` 消费者
- 应用场景：用户注册后异步发送通知、日志异步落库、（进阶）订单超时自动取消的延迟队列
- 消息可靠性基本概念：ACK 确认机制、幂等消费

**里程碑**：用户注册成功后，通过 MQ 异步"发送欢迎通知"（可以只是模拟打印/写表，不需要真接短信服务）。

**踩坑提醒**
- 消费者处理逻辑要考虑幂等：网络抖动可能导致同一条消息被重复消费
- 不是所有异步场景都要上 MQ，能同步搞定的简单逻辑没必要为了炫技强行异步化

---

### 阶段 8（第 7 周）｜定时任务与文件存储

**学什么**
- `@Scheduled` 基本用法（cron 表达式）；如果想体验更"企业级"的方案，了解一下 XXL-JOB 的定位（分布式、可视化、失败重试）
- 文件上传下载：先做本地存储版本，再升级成 MinIO（用 Docker 一条命令就能起一个 S3 兼容的对象存储）

**里程碑**：定时任务清理"过期未处理"的数据或生成简单报表；用户头像上传改为存到 MinIO，返回可访问的 URL。

---

### 阶段 9（第 8 周）｜容器化部署：Docker

**学什么**
- Dockerfile 编写
- Docker Compose 把 MySQL + Redis + RabbitMQ + MinIO + 应用编排在一起
- 基本的 Nginx 反向代理配置（可选，用来模拟真实的前后端分离部署）

**里程碑**：一条 `docker-compose up -d` 命令能拉起完整的开发/测试环境。

**踩坑提醒**
- 容器间通信要用服务名而不是 `localhost`（比如应用连 Redis 要写 `redis` 而不是 `127.0.0.1`）
- 记得给数据卷（volume）做持久化，不然容器一删数据就没了

---

### 阶段 10（进阶·可选）｜微服务化：Spring Cloud Alibaba

如果基础版做完还有余力，或者目标是准备涉及微服务的面试，可以考虑把单体拆分：
- Nacos 做注册中心 + 配置中心
- Spring Cloud Gateway 做统一网关（顺便把 JWT 校验这类横切逻辑挪到网关层）
- OpenFeign 做服务间调用
- Sentinel 做限流熔断

这一步复杂度会明显上一个台阶，建议先把单体版本的企业级 Demo 做扎实，再决定要不要跨到微服务（若依也提供 RuoYi-Cloud 这个微服务版本可以对照）。

---

## 4. 贯穿全程：单元测试怎么搞

不建议单独留到最后"补测试"，而是每完成一个模块的 Service 层，顺手用 JUnit 5 + Mockito 写几个核心方法的单元测试，尤其是有业务逻辑分支的地方（权限校验、库存扣减这类）。企业代码评审很看重这一点，也是简历上能拿出来说的细节。

---

## 5. 参照的开源项目

自己做完核心模块之后，非常建议对照一两个成熟的开源项目，看看别人是怎么处理同样问题的（不建议一上来就直接抄代码，独立做完再对照阅读，收获会大很多）：

- **若依 RuoYi**：`doc.ruoyi.vip`（文档）、`gitee.com/y_project/RuoYi-Vue`（源码）。经典的权限管理系统标杆，提供 Spring Boot 2.x/3.x/4.x 多版本分支并行维护，也有对应的微服务版 RuoYi-Cloud。
- **ruoyi-vue-pro（原芋道 yudao）**：`github.com/YunaiV/ruoyi-vue-pro`。基于若依重构，功能更丰富（数据权限、多租户、工作流等），代码风格更偏企业级，适合进阶阅读。
- **MyBatis-Plus 官方文档**：`baomidou.com`，中文原生、示例详尽。
- **Knife4j 官方文档**：`doc.xiaominfo.com`，Spring Boot 2/3/4 各版本的接入方式都有说明。

---

## 6. 自查清单：怎么算"像个企业级 Demo"了

- [ ] 接口返回格式统一，异常有全局处理，不会裸抛 500 堆栈给前端
- [ ] 密码等敏感信息加密存储，接口有鉴权，未授权访问会被拦截
- [ ] 关键操作有日志可查
- [ ] 至少一个真实的缓存场景 + 一个分布式锁场景
- [ ] 至少一个异步/消息队列场景
- [ ] 有接口文档，别人不需要问你就能看懂怎么调用
- [ ] 能用 Docker Compose 一键起环境
- [ ] 核心 Service 方法有单元测试覆盖
- [ ] README 里写清楚技术栈、架构图、如何启动

---

## 7. 写在最后

建议每完成一个阶段就提交一次 Git commit（commit message 直接写"完成阶段 N：xxx"），一是方便回滚，二是完成之后这个提交历史本身就是一份很好的项目说明，可以直接放进作品集或者简历附带的 GitHub 链接里。
