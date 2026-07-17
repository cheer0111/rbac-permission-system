# Spring Boot 企业级项目技术栈实现指南

> 本文档基于一个真实项目（RBAC 权限管理系统），从零讲解项目中用到的每一项技术。假设你刚学完 Spring Boot 基础（知道怎么写 Controller、Service、Mapper），跟着这份指南可以理解一个"接近生产"的项目是怎么搭起来的。

---

## 目录

- [第一章：项目概览](#第一章项目概览)
- [第二章：工程规范化 — Result/全局异常/参数校验](#第二章工程规范化)
- [第三章：MyBatis-Plus — ORM 框架](#第三章mybatis-plus)
- [第四章：数据库设计 — RBAC 权限模型](#第四章数据库设计--rbac-权限模型)
- [第五章：Spring Security + JWT — 认证授权](#第五章spring-security--jwt)
- [第六章：Redis 缓存 + Redisson 分布式锁](#第六章redis-缓存--redisson)
- [第七章：AOP 自定义注解 + 操作日志](#第七章aop-自定义注解--操作日志)
- [第八章：Knife4j 接口文档](#第八章knife4j-接口文档)
- [第九章：项目架构全景图](#第九章项目架构全景图)
- [附录：常用问题排查清单](#附录常用问题排查清单)

---

## 第一章：项目概览

### 1.1 项目是什么？

一个**基于 RBAC（基于角色的访问控制）的权限管理系统后端**。没有前端页面，所有接口通过 Knife4j/Postman 测试。

核心能力：
- 用户管理（增删查）
- 角色管理（关联权限）
- 菜单/权限管理（树形结构）
- 登录认证（JWT）
- 接口权限控制（按钮级别）
- 操作日志审计
- 防重复提交

### 1.2 技术栈一览

| 技术 | 版本 | 作用 | 对应阶段 |
|------|------|------|----------|
| Spring Boot | 3.5.x | 基础框架 | 全局 |
| MyBatis-Plus | 3.5.10 | ORM 框架，操作数据库 | 阶段2 |
| MySQL | 8.0+ | 关系型数据库 | 全局 |
| Druid | 1.2.23 | 数据库连接池 + 监控 | 阶段2 |
| Spring Security | 6.x | 安全框架（认证+授权） | 阶段4 |
| jjwt | 0.12.6 | JWT 令牌生成与解析 | 阶段4 |
| Redis | — | 缓存 + 分布式锁 | 阶段5 |
| Redisson | 3.27.0 | Redis 分布式锁客户端 | 阶段5 |
| Knife4j | 4.4.0 | 接口文档 | 阶段6 |
| Lombok | — | 简化 Java 代码 | 全局 |

### 1.3 项目包结构

```
cheer.demo
├── cheer.controller        # 控制器（接收请求，返回响应）
├── cheer.service           # 服务接口
│   └── cheer.service.impl  # 服务实现（业务逻辑）
├── cheer.mapper            # Mapper 接口（操作数据库）
├── cheer.entity            # 实体类（对应数据库表）
├── cheer.dto               # 数据传输对象（接收前端参数）
├── cheer.vo                # 视图对象（返回给前端的定制格式）
├── cheer.common            # 公共模块
│   ├── annotation          # 自定义注解
│   ├── aspect              # AOP 切面
│   ├── config              # 配置类
│   ├── enums               # 枚举
│   ├── exception           # 自定义异常
│   ├── filter              # 过滤器
│   ├── handle              # 处理器（全局异常、自动填充）
│   ├── result              # 统一响应封装
│   └── utils               # 工具类
└── resources
    ├── application.yml     # 配置文件
    └── sql/                # 数据库脚本
```

### 1.4 为什么这样分包？

这是**按技术层分包**（Layer-first），也叫"若依风格"。好处是：

- Controller 放一起，一眼知道有哪些接口
- Service 放一起，业务逻辑集中
- common 放一起，公共组件复用

如果你的项目将来变大（几十个业务模块），可以改成**按业务分包**（Module-first），比如 `user/`、`role/`、`menu/` 下各放自己的 Controller/Service/Mapper。

---

## 第二章：工程规范化

> 源码参考：`Result.java`、`ResultCode.java`、`BusinessException.java`、`GlobalExceptionHandler.java`、`MyMetaObjectHandler.java`

### 2.1 为什么需要统一响应格式？

没有规范时，你的接口返回可能是这样的：

```json
// 接口A返回的
{ "data": {...}, "code": 200, "message": "成功" }

// 接口B返回的
{ "result": {...}, "status": "ok" }

// 接口C返回的
{ "error": "出错了" }
```

前端根本没法统一处理。**统一响应格式**让所有接口长一个样子：

```json
{
  "data": { "id": 1, "username": "zhangsan" },
  "code": 200,
  "message": "操作成功"
}
```

### 2.2 Result 统一响应类

```java
@Data
public class Result<T> {
    private T data;      // 响应数据
    private int code;     // 状态码
    private String message; // 提示信息

    // 成功时调用
    public static <T> Result<T> success(T data, ResultCode code, String message) {
        return new Result<>(data, code.getCode(), message);
    }

    // 失败时调用
    public static <T> Result<T> error(ResultCode code, String message) {
        return new Result<>(code.getCode(), message);
    }
}
```

Controller 里这样用：

```java
@GetMapping("/user/page")
public Result<Page<User>> getUserPage(...) {
    Page<User> userPage = userService.query(...);
    return Result.success(userPage, ResultCode.SUCCESS, "查看成功");
}
```

### 2.3 ResultCode 状态码枚举

把所有用到的状态码集中管理：

```java
public enum ResultCode {
    SUCCESS(200, "请求成功"),
    PARAM_ERROR(400, "请求参数错误"),
    UNAUTHORIZED(401, "未认证或登录已过期"),
    FORBIDDEN(403, "没有访问权限"),
    DATA_EXISTS(409, "数据已存在"),
    SERVER_ERROR(500, "服务器内部错误");
}
```

**为什么不用直接写数字 200、400？** 好维护、好搜索、减少魔法值。

### 2.4 BusinessException 业务异常

当业务规则不满足时（如用户名已存在），不是"系统出错了"，而是"业务上不允许"。这时候抛自定义异常：

```java
// 用户名查重
if (count > 0) {
    throw new BusinessException(ResultCode.DATA_EXISTS, "用户名已存在");
}
```

### 2.5 GlobalExceptionHandler 全局异常处理器

如果每个 Controller 方法都写 try-catch，代码会非常冗余。**全局异常处理器**用 `@RestControllerAdvice` + `@ExceptionHandler` 一劳永逸地拦截所有异常：

```
请求 → Controller → 抛出 BusinessException
                       ↓
              GlobalExceptionHandler 捕获
                       ↓
              返回 { code: 409, message: "用户名已存在" }
```

按优先级拦截三种异常：

| 优先级 | 异常类型 | 说明 |
|--------|---------|------|
| 最高 | `BusinessException` | 业务异常，返回 code + 自定义 message |
| 中 | `MethodArgumentNotValidException` | 参数校验失败，返回每个字段的错误 |
| 最低 | `Exception` | 兜底，返回 500，**不暴露错误细节给前端** |

### 2.6 MyMetaObjectHandler 自动填充

每次 insert 都手动写 `user.setCreateTime(LocalDateTime.now())` 很烦。MP 的自动填充功能让你只需要在实体字段上加注解：

```java
@TableField(fill = FieldFill.INSERT)
private LocalDateTime createTime;

@TableField(fill = FieldFill.INSERT_UPDATE)
private LocalDateTime updateTime;
```

然后写一个 `MetaObjectHandler`：

```java
@Component
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, LocalDateTime.now());
        this.strictInsertFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        this.strictUpdateFill(metaObject, "updateTime", LocalDateTime.class, LocalDateTime.now());
    }
}
```

**`strictInsertFill` vs `strictUpdateFill`**：`strict` 表示"字段有值时跳过"。insert 时 `createTime` 如果已经手动设了值，就不再覆盖。

### 2.7 实战：从零实现一个分页查询接口（端到端流程）

这是你**最常用的开发模式**。以后每次新增一个功能，都按这个流程走。以"用户分页查询"为例：

#### 第一步：写 DTO（接收前端参数）

```java
@Data
public class UserQueryDTO {
    private String username;    // 模糊搜索（可选）
    private Integer status;     // 精确过滤（可选）
    private Integer pageNum = 1;  // 当前页码，默认第1页
    private Integer pageSize = 10; // 每页条数，默认10条
}
```

**为什么要 DTO？** Controller 方法的参数应该是"前端需要传什么"，而不是"数据库长什么样"。比如前端不需要传 `delFlag`、`createTime` 这些字段。

#### 第二步：写 Controller（接收请求、返回响应）

```java
@RestController
@RequestMapping("/user")
@Tag(name = "用户管理")
public class UserController {

    @Resource
    private UserService userService;

    @GetMapping("/page")
    @Operation(summary = "分页查询用户")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<Page<UserVO>> getUserPage(@Validated UserQueryDTO dto) {
        Page<UserVO> page = userService.queryPage(dto);
        return Result.success(page, ResultCode.SUCCESS, "查询成功");
    }
}
```

逐行解释：
- `@RestController` = `@Controller` + `@ResponseBody`（返回值自动转 JSON）
- `@RequestMapping("/user")` = 该类所有接口的前缀都是 `/user`
- `@Resource` = 注入 Service（和 `@Autowired` 功能一样，推荐前者）
- `@GetMapping("/page")` = 完整路径是 `GET /user/page`
- `@Validated` = 触发 DTO 里的参数校验（如 `@NotBlank`）
- `@PreAuthorize` = 权限检查（第五章详解）
- 返回 `Result<Page<UserVO>>` = 统一响应格式包装

#### 第三步：写 Service（业务逻辑）

```java
public interface UserService {
    Page<UserVO> queryPage(UserQueryDTO dto);
}
```

```java
@Service
public class UserServiceImpl implements UserService {

    @Resource
    private UserMapper userMapper;

    @Override
    public Page<UserVO> queryPage(UserQueryDTO dto) {
        // 1. 创建 MP 分页对象（传给 MyBatis-Plus 的参数）
        Page<User> page = new Page<>(dto.getPageNum(), dto.getPageSize());

        // 2. 构造查询条件
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        wrapper.like(StringUtils.isNotBlank(dto.getUsername()),
                     User::getUsername, dto.getUsername())    // username 非空才加条件
               .eq(dto.getStatus() != null,
                   User::getStatus, dto.getStatus())           // status 非空才加条件
               .orderByDesc(User::getCreateTime);             // 按创建时间降序

        // 3. 执行查询（MP 自动拼接 SQL + 分页）
        Page<User> result = userMapper.selectPage(page, wrapper);

        // 4. Entity → VO 转换（隐藏密码等敏感字段）
        Page<UserVO> voPage = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        List<UserVO> voList = result.getRecords().stream()
                .map(user -> {
                    UserVO vo = new UserVO();
                    BeanUtils.copyProperties(user, vo);
                    return vo;
                })
                .collect(Collectors.toList());
        voPage.setRecords(voList);

        return voPage;
    }
}
```

**核心流程：**
```
DTO → Page对象 + LambdaQueryWrapper → Mapper.selectPage() → Entity列表 → VO列表
```

**为什么要 VO？** Entity 对应数据库表（含 password），VO 对应前端需要（不含 password）。两者解耦，互不影响。

#### 第四步：Mapper（MP 自动完成，不用写代码）

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 什么都不用写！selectPage 方法 BaseMapper 已经提供了
}
```

#### 整个流程的 SQL 是什么？

MP 根据你的 Wrapper 自动生成：

```sql
-- wrapper 构造条件 + 分页
SELECT id, username, password, status, del_flag, create_time, update_time
FROM sys_user
WHERE del_flag = 0                          -- @TableLogic 自动追加
  AND username LIKE '%zhang%'               -- wrapper.like
  AND status = 1                             -- wrapper.eq
ORDER BY create_time DESC                    -- wrapper.orderByDesc
LIMIT 0, 10                                 -- 分页（Page<1,10> → LIMIT offset, size）
```

> **你一行 SQL 都没写**，MP 帮你全部搞定了。

### 2.8 本章小结

```
Controller 返回 Result → 业务不满足时抛 BusinessException → GlobalExceptionHandler 捕获 → 返回统一格式
```

这就是工程规范化的核心：**统一入口、统一出口、统一异常处理**。

---

## 第三章：MyBatis-Plus

> 源码参考：`MybatisPlusConfig.java`、`UserServiceImpl.java`、`UserMapper.java`

### 3.1 MyBatis-Plus 是什么？

MyBatis-Plus（简称 MP）是 MyBatis 的增强工具，**只增强不做改变**。你原来怎么用 MyBatis 现在还怎么用，只是简化了单表 CRUD。

最强大的功能：你**不用写 SQL**，MP 自动根据实体类生成。

### 3.2 核心概念

#### BaseMapper

你的 Mapper 接口继承 `BaseMapper<T>`，就自动拥有了基本的 CRUD 方法：

```java
@Mapper
public interface UserMapper extends BaseMapper<User> {
    // 不用写任何方法！已经有了：
    // insert(entity)        → INSERT
    // deleteById(id)       → DELETE（有 @TableLogic 时转 UPDATE）
    // selectById(id)       → SELECT
    // selectPage(page, wrapper) → 分页查询
    // selectList(wrapper)   → 列表查询
    // selectCount(wrapper)  → 计数
}
```

#### 实体类注解

```java
@Data
@TableName("sys_user")          // 对应哪张表
public class User {
    @TableId(type = IdType.ASSIGN_ID)  // 主键策略：雪花算法（自动生成分布式唯一ID）
    private Long id;

    @TableLogic                       // 逻辑删除标记
    private Integer delFlag;          // deleteById 时不会真删，而是 UPDATE SET del_flag=1

    @TableField(fill = FieldFill.INSERT)  // 自动填充
    private LocalDateTime createTime;
}
```

#### 分页插件

MP 的分页需要在配置类中注册插件，否则 `selectPage` 不会生效：

```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

### 3.3 LambdaQueryWrapper 条件构造器

MP 提供了 LambdaQueryWrapper，让你用 Java 方法引用写查询条件，**编译器帮你检查字段名**，不会拼错：

```java
// 以前 MyBatis 写 XML：
// <if test="username != null">AND username LIKE #{username}</if>

// 现在这样写：
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(User::getStatus, 1)                      // WHERE status = 1
       .like(User::getUsername, "zhang")            // AND username LIKE '%zhang%'
       .between(User::getCreateTime, start, end)    // AND create_time BETWEEN ? AND ?
List<User> users = userMapper.selectList(wrapper);
```

常用方法：

| 方法 | 对应 SQL | 说明 |
|------|----------|------|
| `eq` | `=` | 等于 |
| `ne` | `<>` | 不等于 |
| `like` | `LIKE '%xxx%'` | 模糊匹配 |
| `in` | `IN (?, ?, ...)` | 在集合内 |
| `between` | `BETWEEN ? AND ?` | 范围查询 |
| `orderByAsc` | `ORDER BY xxx ASC` | 升序 |
| `orderByDesc` | `ORDER BY xxx DESC` | 降序 |

### 3.4 @TableLogic 逻辑删除

为什么要逻辑删除而不是真删？

- 用户删了，但他关联的订单、日志还在，真删会导致数据关联断裂
- 删了之后还能恢复

在字段上加 `@TableLogic` 后：

```java
@TableLogic
private Integer delFlag;  // 0=正常  1=已删除
```

**你调用 `deleteById(id)` 时，MP 自动转成：**

```sql
UPDATE sys_user SET del_flag = 1 WHERE id = ?
```

**查询时自动追加：**

```sql
WHERE del_flag = 0
```

你完全不用管这个字段，MP 帮你处理了。

### 3.5 Druid 连接池 + 监控

为什么需要连接池？每次请求都新建数据库连接太慢，连接池提前建好一批连接复用。

Druid 是阿里巴巴的连接池，自带监控页面。配置后访问 `http://localhost/druid` 可以看到：
- SQL 执行记录
- 连接池状态
- 慢查询统计

### 3.6 实战：分页查询全流程拆解

分页是项目中最高频的操作，这里把每个环节拆开讲清楚。

#### 分页查询需要几步？

```
1. 配置分页插件（MybatisPlusConfig，只需写一次）
2. 创建 Page 对象（指定当前页、每页条数）
3. 构建 LambdaQueryWrapper（指定查询条件）
4. 调用 mapper.selectPage(page, wrapper)
5. 从返回的 Page 对象取数据
```

#### 第一步：配置分页插件（一次性配置）

```java
@Configuration
public class MybatisPlusConfig {
    @Bean
    public MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        // 参数 DbType.MYSQL 告诉插件用什么数据库的方言
        // MySQL 用 LIMIT offset, size；Oracle 用 ROWNUM；PostgreSQL 用 OFFSET FETCH
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
```

**如果没注册这个插件会怎样？** `selectPage` 不会报错，但**不会分页**——它会返回全部数据，`Page.getTotal()` 始终为 0。这是初学者最常见的坑。

#### 第二步：创建 Page 对象

```java
// Page<实体类> = 告诉 MP：你要查的是 User 表，第 2 页，每页 5 条
Page<User> page = new Page<>(2, 5);
```

Page 对象的三个关键属性：

| 属性 | 含义 | 示例值 |
|------|------|--------|
| `current` | 当前页码（从1开始） | 2 |
| `size` | 每页条数 | 5 |
| `total` | 总记录数（查完后 MP 自动填充） | 23 |

查完后 `page` 对象还多出 `records`（当前页的数据列表）和 `pages`（总页数）。

#### 第三步：构建查询条件

```java
LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
// 条件方法都有一个 boolean 参数：true 才加这个条件，false 跳过
// 这样就不需要写 if (dto.getUsername() != null) 这种判断了
wrapper.like(StringUtils.isNotBlank(dto.getUsername()),  // boolean: 用户名非空？
             User::getUsername, dto.getUsername())        // true 时追加: AND username LIKE '%xxx%'
       .eq(dto.getStatus() != null,                        // boolean: 状态非空？
           User::getStatus, dto.getStatus())                // true 时追加: AND status = ?
       .orderByDesc(User::getCreateTime);                  // 排序（无条件参数，始终追加）
```

**条件参数的意义：** 把 `if` 判断写在 Wrapper 内部，代码更简洁。如果 `dto.getUsername()` 是空字符串，`StringUtils.isNotBlank()` 返回 false，`.like()` 这行不生效，SQL 中不会有 `LIKE` 条件。

#### 第四步：执行查询

```java
Page<User> result = userMapper.selectPage(page, wrapper);
```

这一行背后，MP 做了两件事：

```sql
-- 第1步：先查总数（用于前端显示"共 XX 条"）
SELECT COUNT(*) FROM sys_user WHERE del_flag = 0 AND username LIKE '%zhang%'

-- 第2步：再查分页数据（跳过前面的，取当前页的）
SELECT * FROM sys_user WHERE del_flag = 0 AND username LIKE '%zhang%'
ORDER BY create_time DESC LIMIT 5, 5
-- LIMIT offset, size → LIMIT (pageNum-1)*pageSize, pageSize → LIMIT 5, 5
```

#### 第五步：取结果

```java
result.getRecords();  // 当前页的数据列表 List<User>
result.getTotal();    // 总记录数（long）
result.getPages();    // 总页数（long）
result.getCurrent();  // 当前页码（long）
result.getSize();     // 每页条数（long）
```

**MP 的分页和手动 LIMIT 的区别：**

```java
// 手动 LIMIT（不推荐，不知道总数）
List<User> list = mapper.selectList(wrapper.last("LIMIT 0, 10"));
// 你只知道这 10 条，不知道总共多少条、还有没有下一页

// MP 分页（推荐，自动查总数）
Page<User> page = mapper.selectPage(new Page<>(1, 10), wrapper);
// page.getTotal() = 总数，page.getPages() = 总页数，page.hasNext() = 有没有下一页
```

### 3.7 本章小结

| 功能 | 关键注解/配置 | 说明 |
|------|-------------|------|
| CRUD | `BaseMapper<T>` | 不写 SQL 自动 CRUD |
| 分页 | `PaginationInnerInterceptor` | 必须注册插件 |
| 主键 | `@TableId(type = ASSIGN_ID)` | 雪花算法自动生成 |
| 逻辑删除 | `@TableLogic` | delete → UPDATE，查询自动过滤 |
| 自动填充 | `@TableField(fill=...)` | createTime/updateTime 自动 |
| 条件查询 | `LambdaQueryWrapper` | 类型安全，编译检查 |

---

## 第四章：数据库设计 — RBAC 权限模型

> 源码参考：`rbac_demo.sql`

### 4.1 什么是 RBAC？

RBAC（Role-Based Access Control）= **基于角色的访问控制**。

核心思想：**不直接给用户分配权限，而是给角色分配权限，再把角色分配给用户**。

```
用户 ←→ 角色 ←→ 权限（菜单/按钮）
张三  → 管理员 → 新增用户、删除用户、...
李四  → 普通用户 → 查看用户、...
```

好处：
- 新人入职 → 分配角色即可，不用一个个勾权限
- 换岗 → 修改角色，权限自动变
- 批量管理 → 修改一个角色，所有该角色用户生效

### 4.2 五张表

```
sys_user          用户表
sys_role          角色表
sys_menu          菜单/权限表（支持树形结构）
sys_user_role     用户-角色关联表（多对多）
sys_role_menu     角色-菜单关联表（多对多）
```

#### sys_menu 的树形结构

菜单是分层的：**目录 → 菜单 → 按钮**。通过 `parent_id` 自引用实现：

```
首页 (parent_id = 0)
系统管理 (parent_id = 0)
  ├── 用户管理 (parent_id = 2001)
  │    ├── 用户查询 (parent_id = 2002)   ← 按钮，perms = "system:user:query"
  │    ├── 用户新增 (parent_id = 2002)   ← 按钮，perms = "system:user:add"
  │    └── 用户删除 (parent_id = 2002)   ← 按钮，perms = "system:user:delete"
  ├── 角色管理 (parent_id = 2001)
  │    ├── 角色查询 (parent_id = 2004)
  │    └── 角色新增 (parent_id = 2004)
  └── 菜单管理 (parent_id = 2001)
```

菜单的三种类型：

| menuType | 含义 | 示例 |
|----------|------|------|
| M (目录) | 侧边栏的一级分组 | "系统管理" |
| C (菜单) | 侧边栏的具体页面 | "用户管理" |
| F (按钮) | 页面上的操作按钮 | "新增用户" |

**F 类型才有 `perms` 字段**（权限标识），M 和 C 没有。

### 4.3 权限链路

一个接口要检查用户有没有权限，走的是这条链路：

```
用户ID → sys_user_role → 角色ID列表
       → sys_role_menu → 菜单ID列表
       → sys_menu      → perms 字符串列表
       → 对比 @PreAuthorize 注解中声明的权限
```

### 4.4 实战：权限链路的代码实现

理论上"用户→角色→菜单→权限标识"这个链路涉及多表联查。来看具体代码怎么走的。

#### 登录时：查权限并写入 JWT

```java
// AuthServiceImpl.login() 中：
// 1. 查用户
User user = userMapper.selectOne(
    new LambdaQueryWrapper<User>()
        .eq(User::getUsername, loginDTO.getUsername())
        .eq(User::getDelFlag, 0)
        .eq(User::getStatus, 1)
);
if (user == null || !passwordEncoder.matches(loginDTO.getPassword(), user.getPassword())) {
    throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
}

// 2. 查该用户的所有权限标识（多表联查）
List<String> permissions = menuMapper.selectPermsByUserId(user.getId());
// → SQL:
// SELECT DISTINCT m.perms
// FROM sys_menu m
// INNER JOIN sys_role_menu rm ON m.id = rm.menu_id
// INNER JOIN sys_user_role ur ON rm.role_id = ur.role_id
// WHERE ur.user_id = #{userId} AND m.del_flag = 0 AND m.status = 1

// 3. 把权限列表写入 JWT token
String token = jwtUtil.generateToken(user.getId(), user.getUsername(), permissions);
```

**为什么要把权限存进 JWT？** 这样后续每个请求不用每次查数据库验证权限，直接从 token 里取——无状态、高性能。

#### 请求时：从 JWT 取权限，比对 @PreAuthorize

```
步骤1：JwtAuthenticationFilter 解析 token
    → Claims claims = jwtUtil.parseToken(token)
    → List<String> permissions = claims.get("permissions", List.class)
    → 转成 List<SimpleGrantedAuthority>（Spring Security 的权限对象）
    → 存入 SecurityContextHolder

步骤2：请求到达 Controller 的 addUser 方法
    → @PreAuthorize("hasAuthority('system:user:add')") 注解生效

步骤3：Spring Security 的 AOP 切面拦截
    → 从 SecurityContextHolder 取出当前用户的 authorities
    → authorities 里有没有 "system:user:add"？
    → 有：放行，执行方法
    → 没有：抛 AccessDeniedException → GlobalExceptionHandler → 403
```

**完整的 SQL 层面的权限链路（以 userId=1943123456789012001 为例）：**

```sql
-- 第一步：找到用户的所有角色
SELECT role_id FROM sys_user_role WHERE user_id = 1943123456789012001;
-- 结果：[1]（管理员）

-- 第二步：找到管理员角色的所有菜单权限
SELECT menu_id FROM sys_role_menu WHERE role_id = 1;
-- 结果：[2002, 2003, 2004, 2005, 2006, 2007, 2008, ...]

-- 第三步：找到这些菜单的权限标识
SELECT perms FROM sys_menu WHERE id IN (2002, 2003, ...) AND menu_type = 'F';
-- 结果：["system:user:query", "system:user:add", "system:user:delete", ...]

-- 项目中一条 SQL 搞定上面三步（menuMapper.selectPermsByUserId）
```

### 4.5 主键策略：雪花算法

`@TableId(type = IdType.ASSIGN_ID)` 使用雪花算法生成主键。

为什么不用自增 ID？

| | 自增 ID | 雪花算法 |
|---|---------|---------|
| 格式 | 1, 2, 3... | 1943123456789012001 |
| 分布式 | 依赖数据库，多库会冲突 | 不依赖数据库，全局唯一 |
| 安全 | 能猜到下一个ID | 不可猜测 |

---

## 第五章：Spring Security + JWT

> 源码参考：`SecurityConfig.java`、`JwtAuthenticationFilter.java`、`JwtUtil.java`、`AuthServiceImpl.java`

### 5.1 为什么需要认证授权？

没有认证：任何人都能调你的接口，删库、改数据随便来。

Spring Security 是 Spring 生态的安全框架，负责两件事：
1. **认证（Authentication）**：你是谁？（登录）
2. **授权（Authorization）**：你能做什么？（权限检查）

### 5.2 传统 Session 认证 vs JWT

| | Session | JWT |
|---|---------|-----|
| 存储位置 | 服务器内存/Redis | 客户端（每次请求带上） |
| 服务器压力 | 要维护 Session 存储 | 无状态，不存储 |
| 分布式 | 需要共享 Session | 天然支持 |
| 适用场景 | 传统 Web（有前后端） | 前后端分离/移动端 |

**前后端分离项目用 JWT 是行业标准。**

### 5.3 JWT 的工作原理

```
第1步：登录
  客户端 → POST /auth/login {username, password} → 服务器
  服务器 → 验证成功 → 生成 JWT token → 返回给客户端

第2步：每次请求
  客户端 → GET /user/page + Header: Authorization: Bearer <token> → 服务器
  服务器 → 解析 token → 知道你是谁 → 执行业务 → 返回数据
```

JWT token 长这样：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxOTQz...xxx.abcDefGhiJklm
│──── Header ────││──────── Payload ──────────────││──── Signature ──│
│   算法信息      ││  userId, username, permissions││   签名防篡改    │
```

三段用 `.` 分隔：
1. **Header**：使用什么算法
2. **Payload**：存放的数据（userId、username、permissions）
3. **Signature**：用密钥对前两段的签名，防止篡改

### 5.4 项目中的认证流程

#### JwtUtil — 生成和解析 Token

```java
// 生成：把 userId、username、permissions 写入 token
public String generateToken(Long userId, String username, List<String> permissions) {
    return Jwts.builder()
            .subject(userId.toString())         // sub = userId
            .claim("username", username)        // 自定义字段
            .claim("permissions", permissions) // 自定义字段
            .issuedAt(now)                     // 签发时间
            .expiration(expireDate)            // 过期时间
            .signWith(key)                      // 签名
            .compact();                         // 生成字符串
}

// 解析：从 token 中取出 Claims（也就是 payload）
public Claims parseToken(String token) {
    return Jwts.parser()
            .verifyWith(key)     // 验证签名
            .build()
            .parseSignedClaims(token)
            .getPayload();
}
```

#### JwtAuthenticationFilter — 每次请求解析 Token

这个过滤器在**每个请求到达 Controller 之前**执行：

```
1. 从 Header 取 Authorization: Bearer xxx
2. 如果没有 token → 放行（可能是白名单接口如 /auth/login）
3. 解析 token → 取出 userId、username、permissions
4. 把 permissions 转成 SimpleGrantedAuthority
5. 封装成 UsernamePasswordAuthenticationToken
6. 存入 SecurityContextHolder（Spring Security 的上下文）
```

**SecurityContextHolder 是什么？** 可以理解为一个"当前用户信息暂存区"。存进去之后，后续的权限检查、日志记录都能从这里取。

#### SecurityConfig — 配置哪些路径放行

```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/auth/login").permitAll()   // 登录接口不需要认证
    .requestMatchers("/druid/**").permitAll()       // Druid 监控页放行
    .requestMatchers("/doc.html").permitAll()       // Knife4j 放行
    .anyRequest().authenticated()                 // 其余全部需要认证
)
```

### 5.5 实战：JWT Token 结构详解

一个实际的 JWT token 长这样：

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxOTQzMTIzNDU2Nzg5MDEyMDAxIiwiZXhwIjoxNzIxOTEyMDAwLCJ1c2VybmFtZSI6InpoYW5nc2FuIiwicGVybWlzc2lvbnMiOlsic3lzdGVtOnVzZXI6cXVlcnkiLCJzeXN0ZW06dXNlcjphZGQiLCJzeXN0ZW06dXNlcjpkZWxldGUiXX0.xF3kL9mN2pQ8rStU7vW0yBc4eA6dR1hG3jK5nM8oP0w
```

三段 Base64 解码后：

#### Header（头部）——第一段

```json
{
  "alg": "HS256",    // 使用的算法
  "typ": "JWT"       // 令牌类型
}
```

这部分是固定的，只是告诉解析器"我用了什么算法"。

#### Payload（载荷）——第二段

```json
{
  "sub": "1943123456789012001",      // subject = userId（标准字段）
  "exp": 1721912000,                  // expiration = 过期时间（标准字段）
  "iat": 1721908400,                  // issuedAt = 签发时间（标准字段）
  "username": "zhangsan",            // 自定义字段
  "permissions": [                    // 自定义字段
    "system:user:query",
    "system:user:add",
    "system:user:delete"
  ]
}
```

**注意：** Payload 只是 Base64 编码，不是加密！任何人都能解码看到内容。所以**千万不要在 token 里放密码等敏感信息**。

#### Signature（签名）——第三段

```
HMACSHA256(
  base64UrlEncode(header) + "." + base64UrlEncode(payload),
  your-256-bit-secret-key    ← application.yml 中配置的密钥
)
```

签名的作用：**防篡改**。如果有人改了 Payload 中的 permissions（比如加了个 admin 权限），签名对不上，服务器拒绝这个 token。

#### 项目中的 Token 生成代码逐行解释

```java
public String generateToken(Long userId, String username, List<String> permissions) {
    // 1. 准备密钥（从 yml 的 jwt.secret 读取，用 HMAC-SHA256 算法）
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

    // 2. 构造 token
    return Jwts.builder()
            .subject(userId.toString())                  // sub = userId，标准字段
            .claim("username", username)                  // 自定义字段：用户名
            .claim("permissions", permissions)             // 自定义字段：权限列表
            .issuedAt(new Date())                          // iat = 当前时间
            .expiration(new Date(System.currentTimeMillis() + expiration)) // exp = 当前时间+过期时长
            .signWith(key, Jwts.SIG.HS256)                // 用密钥+HS256算法签名
            .compact();                                    // 生成字符串
}
```

#### 项目中的 Token 解析代码逐行解释

```java
public Claims parseToken(String token) {
    SecretKey key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));

    return Jwts.parser()
            .verifyWith(key)          // 用同一个密钥验证签名
            .build()                  // 构建 parser
            .parseSignedClaims(token) // 解析并验证 token
            .getPayload();            // 签名验证通过后，返回 Payload（Claims 对象）
}
```

**如果 token 被篡改、过期、或者格式错误**，`parseSignedClaims` 会抛异常 → 被 `JwtAuthenticationFilter` 的 catch 捕获 → 不设 SecurityContext → 请求会被 Security 拦截为 401。

### 5.6 方法级权限控制 @PreAuthorize

URL 级别的控制（谁能访问 `/user/add`）由 SecurityConfig 负责。

方法级别的控制（谁能执行 `addUser` 方法）由 `@PreAuthorize` 负责：

```java
@PreAuthorize("hasAuthority('system:user:add')")
public Result<User> addUser(...) { ... }
```

执行过程：
```
请求到达 addUser 方法
    ↓
Spring AOP 拦截（@EnableMethodSecurity 注册的切面）
    ↓
从 SecurityContext 取出当前用户的 authorities 列表
    ↓
检查列表中有没有 'system:user:add'
    ├─ 有 → 放行，执行方法
    └─ 没有 → 抛 AccessDeniedException → 403
```

### 5.6 密码加密 BCrypt

**绝对不能明文存密码！** 数据库泄露 = 所有用户密码泄露。

BCrypt 是专门为密码设计的哈希算法：
- 自动加随机盐（同一密码每次生成不同哈希）
- 计算慢（防止暴力破解）

```java
// 注册时加密
user.setPassword(passwordEncoder.encode("123456"));
// → "$2a$10$rhMpHyjx8EXWmYo8UZk71OK.vXeGbnaCnvNkSPFCfyPLDb/AXN6Me"

// 登录时校验
passwordEncoder.matches("123456", storedHash);
// → true
```

### 5.7 完整认证授权流程图

```
客户端 POST /auth/login {username: "zhangsan", password: "123456"}
    ↓
AuthServiceImpl.login()
    ├─ 查数据库 → 找到用户
    ├─ passwordEncoder.matches() → 密码正确
    ├─ 查权限 → ["system:user:add", "system:user:delete", ...]
    └─ jwtUtil.generateToken() → 返回 token
    ↓
客户端保存 token
    ↓
客户端 GET /user/page + Header: Authorization: Bearer <token>
    ↓
JwtAuthenticationFilter
    ├─ 解析 token → userId, username, permissions
    ├─ 构建 Authentication（含 authorities）
    └─ 存入 SecurityContextHolder
    ↓
SecurityConfig URL 检查 → /user/page 不是白名单 → 需要认证 → 已认证，放行
    ↓
@PreAuthorize("hasAuthority('system:user:query')")
    ├─ 从 SecurityContext 取 authorities
    └─ 检查有 'system:user:query' → 放行
    ↓
Controller 执行 → 返回分页数据
```

---

## 第六章：Redis 缓存 + Redisson 分布式锁

> 源码参考：`RedisConfig.java`、`MenuServiceImpl.java`、`PreventDuplicateAspect.java`

### 6.1 为什么需要缓存？

有些数据查询很慢或查询频率很高（如菜单树），每次都查数据库没必要。

**缓存策略：读穿透**

```
请求 → 查 Redis → 命中？→ 直接返回
                   ↓ 未命中
              查数据库 → 写入 Redis（设过期时间）→ 返回
```

### 6.2 项目中的缓存

`MenuServiceImpl` 中有三个缓存：

| 缓存 Key | 内容 | 过期时间 |
|----------|------|----------|
| `menu:tree:all` | 全量菜单列表 | 30 分钟 |
| `user:menuTree:{userId}` | 某用户的菜单树 | 30 分钟 |
| `user:perms:{userId}` | 某用户的权限列表 | 30 分钟 |

降级策略：Redis 挂了也不影响业务，catch 异常后降级查库。

### 6.3 实战：缓存读穿透代码详解

以 `MenuServiceImpl.getMenuTreeByUserId()` 为例，拆解缓存读穿透的具体代码：

```java
@Override
public List<MenuVO> getMenuTreeByUserId(Long userId) {
    // 1. 定义缓存 Key（冒号分隔是 Redis 的命名惯例）
    String cacheKey = "user:menuTree:" + userId;

    // 2. 先查 Redis
    try {
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);

        if (cachedJson != null) {
            // 3. 缓存命中 → 反序列化后直接返回，不查数据库
            return JSON.parseArray(cachedJson, MenuVO.class);
        }
    } catch (Exception e) {
        // 缓存异常不影响业务 → 降级查库
        log.warn("Redis 读取失败，降级查询数据库, key={}", cacheKey, e.getMessage());
    }

    // 4. 缓存未命中 → 查数据库
    List<MenuVO> menuList = this.queryMenuTree(userId);

    // 5. 回写缓存（设 30 分钟过期）
    try {
        stringRedisTemplate.opsForValue().set(
            cacheKey,
            JSON.toJSONString(menuList),
            30, TimeUnit.MINUTES
        );
    } catch (Exception e) {
        log.warn("Redis 写入失败, key={}", cacheKey, e.getMessage());
    }

    // 6. 返回数据库结果
    return menuList;
}
```

#### 为什么要 try-catch 包裹 Redis 操作？

Redis 是"锦上添花"的，不是必需品。如果 Redis 挂了：
- 不 catch → 异常往上抛 → `getMenuTree` 接口直接 500 → 用户连菜单都看不了
- catch 了 → 降级查数据库 → 只是慢一点，功能正常

这就是**缓存降级**策略。生产环境的最佳实践。

#### 缓存过期时间怎么选？

| 数据类型 | 过期时间 | 理由 |
|---------|---------|------|
| 菜单树 | 30 分钟 | 菜单不常变，30 分钟够了 |
| 权限列表 | 30 分钟 | 和菜单树同理 |
| 验证码 | 5 分钟 | 短时效，安全考虑 |
| Session | 2 小时 | 用户活跃期内保持登录 |

**为什么设过期时间？** 不设的话，数据库改了菜单，缓存还是旧的，用户看到的永远是旧数据。设了过期时间，最多 30 分钟后自动刷新。

#### 缓存一致性问题

如果管理员修改了某个角色的菜单权限，缓存怎么更新？

```
方案1（本项目采用的）：等缓存过期自动刷新
  优点：简单
  缺点：最多 30 分钟延迟

方案2：修改时主动删缓存
  管理员修改角色权限 → 删除 menu:tree:all 和该用户相关的缓存 → 下次查询自动重建
  优点：实时
  缺点：代码更复杂，需要知道哪些 key 受影响

方案3：使用 Redis 的 Pub/Sub 通知
  修改者发布消息 → 所有实例监听 → 收到消息后删除本地缓存
  优点：多实例一致性
  缺点：最复杂
```

本项目选择方案1，适合当前规模。后续可以升级到方案2。

### 6.4 为什么需要分布式锁？

"防重复提交"场景：用户快速双击"新增"按钮，如果没锁保护会插入两条相同数据。

普通的 `synchronized` 锁只能锁单机进程。部署多个实例（多台服务器）时锁不住。**Redisson 的分布式锁锁的是 Redis 上的一个 key，所有实例共享。**

### 6.4 @PreventDuplicate 防重复提交

原理：

```
请求到达 @PreventDuplicate(key = "#dto.username")
    ↓
AOP 切面解析 SpEL 表达式：#dto.username → "zhangsan"
    ↓
Redisson tryLock("prevent:duplicate:addUser:zhangsan")
    ├─ 成功（第一次请求）→ 执行方法 → 不释放锁 → 锁 5 秒后自动过期
    └─ 失败（0.3 秒内的第二次请求）→ 直接拒绝 → "操作太频繁"
```

关键设计：
- `tryLock(waitTime=0)`：不等待，抢不到立刻返回 false
- 成功时不释放锁：让锁靠 `leaseTime`（冷却期）自然过期
- 失败时释放锁：让用户可以立即重试

### 6.5 实战：分布式锁代码详解

以 `PreventDuplicateAspect` 的核心代码为例：

```java
@Around("@annotation(preventDuplicate)")
public Object around(ProceedingJoinPoint joinPoint, PreventDuplicate preventDuplicate) throws Throwable {

    // 1. 解析 SpEL 表达式，生成锁的 Key
    //    例如 @PreventDuplicate(key = "#dto.username")
    //    #dto.username → 从方法参数中取值 → "zhangsan"
    //    最终 lockKey = "prevent:duplicate:addUser:zhangsan"
    String lockKey = "prevent:duplicate:" + getLockKey(joinPoint, preventDuplicate);

    // 2. 获取 Redisson 锁对象
    RLock lock = redissonClient.getLock(lockKey);

    // 3. 尝试获取锁
    //    tryLock(waitTime=0, leaseTime=5, TimeUnit=SECONDS)
    //    waitTime=0：不等待，抢不到立刻返回 false
    //    leaseTime=5：锁的持有时间 5 秒，到期自动释放
    boolean acquired = lock.tryLock(0, 5, TimeUnit.SECONDS);

    if (!acquired) {
        // 4a. 没抢到锁 → 说明 5 秒内已有相同请求 → 防重复提交
        throw new BusinessException(ResultCode.FORBIDDEN, "操作太频繁，请勿重复提交");
    }

    try {
        // 4b. 抢到锁 → 执行业务方法
        return joinPoint.proceed();
    } finally {
        // 5. 方法执行完后，释放锁
        //    注意：这里总是释放锁，因为业务方法已经执行完毕
        lock.unlock();
    }
}
```

#### 为什么 waitTime=0？

| waitTime 值 | 行为 | 适用场景 |
|------------|------|---------|
| 0 | 立即返回 true/false | 防重复提交（抢不到就是重复） |
| 3秒 | 等 3 秒再返回 | 抢购场景（排队等锁） |

防重复提交不需要排队，抢不到就是重复操作，直接拒绝。

#### 锁的 Key 设计

```
prevent:duplicate:addUser:zhangsan
│              │           │
│              │           └── SpEL 解析后的具体值
│              └── 方法名（标识是哪个接口）
└── 业务前缀（标识是防重复功能）
```

**Key 的粒度决定了锁的范围：**
- `prevent:duplicate:addUser` → 整个 addUser 接口同一时间只能一个人操作（太粗）
- `prevent:duplicate:addUser:zhangsan` → 同一用户 5 秒内不能重复新增（合适）
- `prevent:duplicate:addUser:zhangsan:192.168.1.1` → 同一用户同一 IP（更细）

#### synchronized vs 分布式锁

```
单机部署：只有一个 Spring Boot 进程
┌─────────────────────────────────┐
│  Thread A → synchronized → ✅   │
│  Thread B → synchronized → ❌等待 │
└─────────────────────────────────┘
// synchronized 够用

多机部署：两个 Spring Boot 实例（Nginx 负载均衡）
┌───────────────────────────┐  ┌───────────────────────────┐
│ 实例1 (8081)               │  │ 实例2 (8082)               │
│ Thread A → synchronized ✅ │  │ Thread C → synchronized ✅ │
│ // 两个实例互不干扰，锁不住！│  │ // 同一用户提交了两次！    │
└───────────────────────────┘  └───────────────────────────┘

加了 Redisson 分布式锁后：
┌───────────────────────────┐  ┌───────────────────────────┐
│ 实例1 (8081)               │  │ 实例2 (8082)               │
│ Thread A → tryLock Redis ✅│  │ Thread C → tryLock Redis ❌│
│ // Redis 是共享的，锁得住！  │  │ // 直接拒绝              │
└───────────────────────────┘  └───────────────────────────┘
         │                                │
         └──── 共享 Redis ─────────────────┘
```

### 6.6 SpEL 表达式

`#dto.username` 是 Spring Expression Language（SpEL），从方法参数中取值：

```
@PreventDuplicate(key = "#dto.username", ...)
public void addUser(UserDTO dto) { ... }
//                    ↑ 从参数名 "dto" 中取 username 字段
```

---

## 第七章：AOP 自定义注解 + 操作日志

> 源码参考：`OperationLogAspect.java`、`OperateLogService.java`

### 7.1 什么是 AOP？

AOP（Aspect-Oriented Programming，面向切面编程）。

**类比**：你写了一本书（业务代码），AOP 就像是在每页书后面贴一张便签（额外功能），不用修改书的内容。

项目中你用了两次 AOP：
1. `@PreventDuplicate` → 防重复提交（Redisson 锁）
2. `@OperationLog` → 操作日志记录（异步写数据库）

### 7.2 操作日志的原理

```
Controller 方法加了 @OperationLog(title = "用户管理", businessType = 1)
    ↓
AOP 切面拦截 @Around
    ↓
1. 记录开始时间
2. 执行原方法
3. 从 SecurityContext 取操作人
4. 从 RequestContextHolder 取 URL、HTTP 方法、IP
5. Jackson 序列化请求参数（password 脱敏）
6. Jackson 序列化返回结果
7. 组装 OperateLog 对象
8. @Async 异步写入数据库
    ↓
如果方法抛异常 → 记录 error_msg → 重新抛出（不吞异常）
```

如果方法抛异常 → 记录 error_msg → 重新抛出（不吞异常）
```

### 7.3 实战：操作日志切面核心代码拆解

`OperationLogAspect` 是项目中代码量最大的切面，逐段拆解：

#### 第一部分：前置准备——获取方法上的注解

```java
@Around("@annotation(operationLog)")  // 拦截所有加了 @OperationLog 的方法
public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
    // joinPoint = 当前正在执行的方法的"快照"（方法信息、参数等）
    // operationLog = @OperationLog 注解实例（里面有 title、businessType 等属性）
```

**`@Around` vs `@Before` / `@After`：**
- `@Before`：方法执行前拦截
- `@After`：方法执行后拦截
- `@Around`：**前后都能拦截**，还能决定是否执行原方法、修改返回值——最强大

#### 第二部分：获取当前操作人

```java
// 从 Spring Security 上下文取当前登录用户
Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
String operName = authentication != null ? authentication.getName() : "未知用户";
```

**注意点：** 这个取的是 `JwtAuthenticationFilter` 存进去的用户名。如果请求没有 token（白名单接口），`authentication` 可能是匿名的。

#### 第三部分：获取 HTTP 请求信息

```java
// Spring 提供的工具类，从当前线程绑定的 HttpServletRequest 中取信息
ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
if (attributes != null) {
    HttpServletRequest request = attributes.getRequest();
    String operUrl = request.getRequestURI();        // 如 /user/add
    String method = request.getMethod();              // 如 POST
    String ip = request.getRemoteAddr();              // 客户端 IP（如 192.168.1.100）
}
```

**为什么能通过 `RequestContextHolder` 取到 HttpServletRequest？** Spring 把当前请求的对象绑定到了当前线程（ThreadLocal）。在 AOP 切面里拿到的线程和 Controller 是同一个线程，所以能取到。

#### 第四部分：序列化请求参数（password 脱敏）

```java
// 将方法参数列表序列化为 JSON
private String toJsonString(Object[] args, MethodSignature signature) {
    Object[] argsArray = args;
    String[] paramNames = signature.getParameterNames();

    // 跳过不适合序列化的对象
    if (argsArray == null || argsArray.length == 0) return "{}";

    Map<String, Object> paramMap = new LinkedHashMap<>();
    for (int i = 0; i < argsArray.length; i++) {
        Object arg = argsArray[i];
        // HttpServletRequest、HttpServletResponse、MultipartFile 不能序列化
        if (arg instanceof HttpServletRequest || arg instanceof HttpServletResponse || arg instanceof MultipartFile) {
            continue;
        }
        // 密码字段脱敏
        if (arg != null && arg.toString().contains("password")) {
            // 这里通过反射或 Map 遍历，把 password 字段值替换为 "******"
        }
        paramMap.put(paramNames[i], arg);
    }
    return objectMapper.writeValueAsString(paramMap);
}
```

**为什么要脱敏？** 操作日志存在数据库里，如果明文记录密码，数据库泄露 = 密码泄露。

#### 第五部分：执行原方法 + 组装日志

```java
long startTime = System.currentTimeMillis();
Object result = null;
Throwable exception = null;

try {
    result = joinPoint.proceed();  // ← 执行真正的业务方法（如 addUser）
} catch (Throwable e) {
    exception = e;               // 保存异常，后面记录到日志
}

long costTime = System.currentTimeMillis() - startTime;

// 组装日志对象
OperateLog log = new OperateLog();
log.setTitle(operationLog.title());          // 注解上的 title
log.setBusinessType(operationLog.businessType());  // 1=新增, 2=修改, 3=删除
log.setMethod(joinPoint.getSignature().getDeclaringTypeName() + "." + joinPoint.getSignature().getName());
// → "cheer.controller.UserController.addUser"
log.setOperName(operName);
log.setOperUrl(operUrl);
log.setRequestMethod(requestMethod);
log.setOperParam(toJsonString(...));          // 请求参数 JSON
log.setOperResult(objectMapper.writeValueAsString(result));  // 返回结果 JSON
log.setStatus(exception == null ? 1 : 0);      // 1=正常, 0=异常
log.setErrorMsg(exception != null ? exception.getMessage() : null);
log.setCostTime(costTime);

// 异步保存到数据库
operateLogService.save(log);

// 如果业务方法抛了异常，重新抛出（日志记录不影响业务异常传播）
if (exception != null) {
    throw exception;
}
return result;
```

#### 为什么操作日志表不需要 del_flag 和 update_time？

```
普通业务表（如 sys_user）：
  → 数据会被修改、删除 → 需要 update_time 记录何时修改 → 需要 del_flag 逻辑删除

操作日志表（sys_operate_log）：
  → 一次写入后永远不会改 → 不需要 update_time
  → 日志是审计证据，不应该被"删除" → 不需要 del_flag
  → 如果要清理，用定时任务按日期批量物理删除历史日志
```

### 7.4 @Async 异步

```java
@Async  // 在独立线程中执行，不阻塞主业务
public void save(OperateLog operateLog) {
    operateLogMapper.insert(operateLog);
}
```

**为什么异步？** 日志写入不能拖慢主业务。而且日志写库失败不应该导致业务回滚。

注意：
- 需要在启动类加 `@EnableAsync`
- 异步方法必须在不同的类中调用（Spring AOP 代理限制）

---

## 第八章：Knife4j 接口文档

### 8.1 什么是 Knife4j？

Knife4j 是 Swagger/OpenAPI 的增强版。它**自动根据你的 Controller 代码生成接口文档页面**，还能在线调试。

传统方式：手写接口文档 → 和代码脱节 → 永远更新不及时。

Knife4j 方式：代码写好了，文档自动就有了。

### 8.2 使用方式

1. 加依赖：`knife4j-openapi3-jakarta-spring-boot-starter`
2. 配置 `application.yml`
3. Security 白名单加 Knife4j 路径（`/doc.html`、`/webjars/**` 等）
4. Controller 加注解：
   ```java
   @Tag(name = "用户管理")              // 分组标签（类上）
   @Operation(summary = "新增用户")     // 接口描述（方法上）
   ```
5. 访问 `http://localhost/doc.html`

---

## 第九章：项目架构全景图

### 9.1 一个请求的完整生命周期

```
浏览器/Postman
    │
    ▼
Nginx/Tomcat (HTTP 服务器)
    │
    ▼
Spring DispatcherServlet (统一入口)
    │
    ▼
Filter Chain (过滤器链，按顺序执行)
    ├─ CharacterEncodingFilter      → UTF-8 编码
    ├─ JwtAuthenticationFilter       → 解析 token → SecurityContext
    │
    ▼
Security Filter Chain (Spring Security)
    ├─ 白名单路径？→ permitAll → 直接通过
    └─ 需要认证？→ 检查 SecurityContext 有没有用户 → 没有则 401
    │
    ▼
AOP 代理拦截
    ├─ PreventDuplicateAspect → Redisson 锁检查（如果加了 @PreventDuplicate）
    ├─ @PreAuthorize → 方法级权限检查（如果加了注解）
    └─ OperationLogAspect → 记录操作日志（如果加了 @OperationLog）
    │
    ▼
Controller (接收请求)
    │
    ▼
Service (业务逻辑)
    │
    ▼
Mapper (MyBatis-Plus)
    │
    ▼
MySQL / Redis (数据存储)
    │
    ▼
Result → Jackson 序列化 JSON → HTTP 响应
```

### 9.2 项目技术依赖关系图

```
Spring Boot 3.5
    │
    ├── MyBatis-Plus ──── MySQL
    │       │
    │       └── Druid (连接池 + 监控)
    │
    ├── Spring Security
    │       ├── JWT (jjwt) → 无状态认证
    │       ├── BCrypt → 密码加密
    │       └── @PreAuthorize → 方法级权限
    │
    ├── Redis (Lettuce)
    │       ├── StringRedisTemplate → 缓存
    │       └── Redisson → 分布式锁
    │
    ├── Spring AOP
    │       ├── @PreventDuplicate → 防重复提交
    │       └── @OperationLog → 操作日志
    │
    ├── Knife4j (OpenAPI 3) → 接口文档
    │
    └── Lombok → 代码简化
```

### 9.3 数据流转图（RBAC 权限链路）

```
                    sys_user                  sys_role
                  ┌──────────┐              ┌──────────┐
                  │ zhangsan │──────────────│  管理员   │
                  └──────────┘  sys_user_role └──────────┘
                       │       ┌───────────┐       │
                       │       │ user_id   │ role_id│
                       │       └───────────┘       │
                       │                          │
                       │    sys_role_menu          │
                       │    ┌───────────┐──────────┐│
                       │    │ role_id   │ menu_id  ││
                       │    └───────────┘          ││
                       │                          ▼│
                       │              sys_menu         │
                       │          ┌──────────────┐    │
                       └─────────→ │ 用户管理(C)   │ ←──┘
                                  │ ├ 用户查询(F) │     perms: system:user:query
                                  │ ├ 用户新增(F) │     perms: system:user:add
                                  │ └ 用户删除(F) │     perms: system:user:delete
                                  └──────────────┘
                                          │
                    JWT token 携带 permissions
                          ["system:user:query",
                           "system:user:add",
                           "system:user:delete"]
                                          │
                    Controller 的 @PreAuthorize 检查
                    hasAuthority("system:user:add") → 通过 ✅
```

---

## 附录：常用问题排查清单

### Q1：应用启动失败

| 错误关键词 | 原因 | 解决 |
|-----------|------|------|
| `Unable to connect to Redis` | Redis 没启动或端口不对 | 启动 Redis，检查 yml 配置 |
| `Client sent AUTH, but no password is set` | yml 中有 `password:` 但 Redis 没设密码 | 删掉 yml 中的 `password:` 行 |
| `Connection refused` | MySQL 没启动或端口不对 | 启动 MySQL，检查 yml 配置 |
| `BeanCreationException: redisson` | Redisson 和 Redis 连接配置不一致 | 统一 Redis 地址配置 |
| `Table 'xxx' doesn't exist` | 没有执行建表 SQL | 在 MySQL 中执行 schema.sql |

### Q2：登录失败

| 现象 | 原因 | 解决 |
|------|------|------|
| 用户名或密码错误 | 密码是明文，但代码用了 BCrypt 校验 | 用 BCrypt 哈希更新数据库密码 |
| 401 未认证 | 没带 token 或 token 格式错误 | Header 格式：`Authorization: Bearer <token>`（注意空格） |
| 403 没有权限 | 当前用户没有接口需要的权限标识 | 检查 sys_role_menu 关联 |

### Q3：接口调用异常

| 现象 | 原因 | 解决 |
|------|------|------|
| 500 参数校验失败 | DTO 字段没加 `@Valid` 或 Controller 方法没加 `@Valid` | 加上即可 |
| 重复提交没有拦截 | Controller 方法没加 `@PreventDuplicate` | 加注解 |
| 操作日志表没有记录 | 忘记加 `@OperationLog` 或 `@Async` 没配置 | 加注解 + 启动类加 `@EnableAsync` |

### Q4：Knife4j 访问不了

| 现象 | 原因 | 解决 |
|------|------|------|
| 403 未登录 | Knife4j 路径没加白名单 | SecurityConfig 中加 `.requestMatchers("/doc.html").permitAll()` |
| 404 | yml 中 knife4j 缩进错误 | `knife4j` 必须是顶级 key，不能嵌套在 `springdoc` 下 |

---

## 更新记录

- 2026-07-17：初始版本，覆盖阶段 1~6 全部技术栈
