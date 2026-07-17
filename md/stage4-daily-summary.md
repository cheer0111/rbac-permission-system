# 今日总结（2026-07-09）— Stage 4：Spring Security + JWT 认证授权

> 本文档记录 Stage 4 的完整实现过程，包含每一步的设计思路、代码实现、踩坑修复。

---

## 一、今日目标

将 Spring Security + JWT 集成到已有的 RBAC 权限系统中，打通完整的认证授权链路：

```
用户登录 → 查数据库 → 生成 JWT（含权限列表） → 返回 token
后续请求 → 携带 token → JwtFilter 解析 → SecurityContext → @PreAuthorize 拦截
```

---

## 二、前置依赖

### 2.1 pom.xml 新增依赖

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT（jjwt 0.12.6 三件套） -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
```

**为什么 jjwt 要三个依赖？**
- `jjwt-api`：接口定义，编译时需要
- `jjwt-impl`：具体实现（如 HS256 签名算法），运行时需要
- `jjwt-jackson`：JSON 序列化/反序列化（JWT payload 是 JSON），运行时需要

### 2.2 application.yml 新增配置

```yaml
jwt:
  secret: cheer-demo-secret-key-for-jwt-token-generation-at-least-32-chars!!
  expiration: 7200000    # 2 小时，单位毫秒
```

---

## 三、核心组件实现

### 3.1 JwtUtil — JWT 工具类

> 职责：生成 token、解析 token

```java
@Component
public class JwtUtil {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private Long expiration;

    /**
     * 生成 Token
     * @param userId      用户ID（作为 subject）
     * @param username    用户名（存入 claims）
     * @param permissions 权限列表（存入 claims）
     */
    public String generateToken(Long userId, String username, List<String> permissions) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        Date now = new Date();
        Date expireDate = new Date(now.getTime() + expiration);

        return Jwts.builder()
                .subject(userId.toString())           // subject 存 userId
                .claim("username", username)           // 自定义 claim 存用户名
                .claim("permissions", permissions)     // 自定义 claim 存权限列表
                .issuedAt(now)                         // 签发时间
                .expiration(expireDate)                // 过期时间
                .signWith(key)                         // 用 HMAC-SHA512 签名
                .compact();                            // 压缩成字符串
    }

    /**
     * 解析 Token，返回 Claims（JWT 的 payload 部分）
     */
    public Claims parseToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));

        return Jwts.parser()
                .verifyWith(key)                    // 设置验证密钥
                .build()
                .parseSignedClaims(token)           // 解析并验证签名
                .getPayload();                      // 返回 payload（Claims）
    }
}
```

**代码解析：**

| 方法 | 关键点 |
|---|---|
| `Keys.hmacShaKeyFor` | 把字符串密钥转为 Java 的 SecretKey 对象。密钥至少 32 字节（256 bit）才能用 HS512 |
| `.subject()` | JWT 标准 claim，通常存用户唯一标识。我们存 userId |
| `.claim()` | 自定义 claim，可以存任意数据。这里存了 username 和 permissions |
| `.signWith(key)` | 使用密钥签名。jjwt 会根据密钥长度自动选择算法（≥32字节用 HS512） |
| `.compact()` | 把 Header、Payload、Signature 三部分用 `.` 拼接成最终字符串 |
| `Jwts.parser().verifyWith(key)` | 解析时用同一把密钥验证签名，防篡改 |

**JWT 三段结构：**
```
Header（算法+类型）. Payload（数据）. Signature（签名）
```

**Payload 解码后示例（张三的管理员 token）：**
```json
{
  "sub": "1943123456789012001",
  "username": "zhangsan",
  "permissions": [
    "system:user:list",
    "system:user:query",
    "system:user:add",
    "system:user:delete",
    "system:role:list",
    "system:role:query",
    "system:role:add",
    "system:menu:list",
    "system:menu:query"
  ],
  "iat": 1783610271,
  "exp": 1783617471
}
```

---

### 3.2 SecurityConfig — Spring Security 配置

> 职责：配置安全规则（哪些路径放行、哪些需认证）、注册 JwtFilter

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity    // 开启 @PreAuthorize 注解支持
public class SecurityConfig {
    @Autowired
    private JwtAuthenticationFilter filter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())          // 前后端分离，禁用 CSRF
                .sessionManagement(session ->
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))  // 无状态，不用 Session
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class) // 注册 JWT 过滤器
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/auth/login").permitAll()    // 登录接口放行
                        .requestMatchers("/druid/**").permitAll()       // Druid 监控放行
                        .anyRequest().authenticated()                  // 其余全部需认证
                );
        return http.build();
    }
}
```

**代码解析：**

| 配置项 | 含义 |
|---|---|
| `@EnableWebSecurity` | 启用 Spring Security 的默认配置 |
| `@EnableMethodSecurity` | 允许在方法上使用 `@PreAuthorize`、`@PostAuthorize` 等权限注解 |
| `csrf.disable()` | CSRF 防护是给表单提交用的，前后端分离 + JWT 不需要 |
| `STATELESS` | 不创建 Session。每次请求都是独立的，通过 token 识别用户 |
| `addFilterBefore` | 在 Security 默认的认证过滤器**之前**插入我们的 JwtFilter |
| `permitAll()` | 白名单路径，不需要 token 也能访问 |
| `authenticated()` | 其余所有路径必须有认证信息（SecurityContext 不为空） |

**过滤器执行顺序：**
```
请求 → DisableEncodeUrlFilter → SecurityContextHolderFilter
     → WebAsyncManagerIntegrationFilter → HeaderWriterFilter
     → LogoutFilter → [JwtAuthenticationFilter ← 我们注册在这里]
     → UsernamePasswordAuthenticationFilter → FilterChainProxy → Controller
```

**为什么放在 UsernamePasswordAuthenticationFilter 之前？**
- Spring Security 默认的认证过滤器是 `UsernamePasswordAuthenticationFilter`
- 它走的是表单用户名密码认证（Session 模式）
- 我们用 JWT 认证，需要在它之前就把 token 解析好、设置好 SecurityContext
- 这样 Security 到后面的过滤器时，就能看到已认证的用户

---

### 3.3 JwtAuthenticationFilter — JWT 认证过滤器

> 职责：从请求头中提取 token → 解析 → 设置 SecurityContext

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    @Autowired
    JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 1. 从请求头获取 Authorization
        String header = request.getHeader("Authorization");

        // 2. 没有 token 或格式不对 → 安静放行，让 Security 拦截
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 提取 token（去掉 "Bearer " 前缀，7个字符）
        String token = header.substring(7);

        // 4. 解析 token
        Claims claims;
        try {
            claims = jwtUtil.parseToken(token);
        } catch (Exception e) {
            // token 无效/过期 → 安静放行，让 Security 拦截
            filterChain.doFilter(request, response);
            return;
        }

        // 5. 从 Claims 中提取信息
        Long userId = Long.valueOf(claims.getSubject());
        String userName = (String) claims.get("username");
        List<String> permissions = (List<String>) claims.get("permissions");

        // 6. 构建 Spring Security 的认证对象
        List<SimpleGrantedAuthority> authorities = permissions.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, null, authorities);

        // 7. 设置到 SecurityContext
        SecurityContextHolder.getContext().setAuthentication(auth);

        // 8. 放行给下一个过滤器
        filterChain.doFilter(request, response);
    }
}
```

**代码解析：**

| 步骤 | 关键点 |
|---|---|
| `extends OncePerRequestFilter` | Spring 提供的过滤器基类，保证每个请求只过滤一次 |
| `header.startsWith("Bearer ")` | 注意有空格！否则 "BearerToken123" 也会匹配 |
| `header.substring(7)` | "Bearer " 是 7 个字符，去掉后就是纯 token |
| `try-catch` | 捕获过期、伪造、格式错误等异常，**不抛出**，安静放行 |
| `SimpleGrantedAuthority` | Spring Security 的权限对象，构造参数就是字符串（如 "system:user:add"） |
| `UsernamePasswordAuthenticationToken` | 认证令牌，三个参数：(principal, credentials, authorities)。principal 放 userId，credentials 放 null（已认证不需要密码），authorities 放权限列表 |
| `SecurityContextHolder` | Spring Security 的上下文持有器，线程级别的 ThreadLocal 存储当前用户的认证信息 |

**核心设计思想：Filter 只做解析，不做拦截。**

```
没有 token  → 放行 → Security 发现 SecurityContext 空 → 403
token 无效  → 放行 → Security 发现 SecurityContext 空 → 403
token 有效  → 设置 SecurityContext → 放行 → Security 发现已认证 → 放行 → Controller
```

---

### 3.4 登录接口 — AuthController + AuthService

#### LoginDTO（接收登录参数）

```java
@Data
public class LoginDTO {
    @NotBlank
    private String username;
    @NotBlank
    private String password;
}
```

#### AuthService（接口）

```java
public interface AuthService {
    String login(LoginDTO dto);
}
```

#### AuthServiceImpl（实现）

```java
@Service
public class AuthServiceImpl implements AuthService {
    @Autowired UserMapper userMapper;
    @Autowired MenuService menuService;
    @Autowired JwtUtil jwtUtil;

    @Override
    public String login(LoginDTO dto) {
        // 1. 查用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, dto.getUsername())
                .eq(User::getDelFlag, 0);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "该用户不存在或该账号已被禁用");
        }

        // 2. 校验密码（当前为明文比对，后续接入 BCrypt）
        if (!user.getPassword().equals(dto.getPassword())) {
            throw new BusinessException(ResultCode.SERVER_ERROR, "密码错误");
        }

        // 3. 查权限链路（复用 MenuService 的方法）
        List<String> permissions = menuService.getPermissionsByUserId(user.getId());

        // 4. 生成 token 返回
        return jwtUtil.generateToken(user.getId(), user.getUsername(), permissions);
    }
}
```

**登录流程图：**
```
POST /auth/login {username, password}
    │
    ├─ 1. 根据 username 查 sys_user（+ del_flag=0）
    │     查不到 → BusinessException("用户不存在")
    │
    ├─ 2. 比对密码
    │     不匹配 → BusinessException("密码错误")
    │
    ├─ 3. 查权限链路（调用 MenuService）：
    │     userId → sys_user_role → roleIds
    │     roleIds → sys_role_menu → menuIds
    │     menuIds → sys_menu → menus
    │     menus → stream().map(Menu::getPerms) → permissions 列表
    │
    └─ 4. jwtUtil.generateToken(userId, username, permissions) → 返回 token
```

#### AuthController

```java
@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {
    @Autowired
    AuthService authService;

    @PostMapping("/login")
    public Result<String> login(LoginDTO loginDTO) {
        String token = authService.login(loginDTO);
        return Result.success(token, ResultCode.SUCCESS, "登录成功");
    }
}
```

**注意：** 这里没有加 `@RequestBody`，所以 Postman 需要用 form-data 方式传参。

---

### 3.5 MenuService 新增方法 — getPermissionsByUserId

```java
// MenuServiceImpl.java

/**
 * 提取用户的所有权限标识（flat list，不建树）
 */
public List<String> getPermissionsByUserId(Long userId) {
    return getMenusByUserId(userId).stream()
            .map(Menu::getPerms)
            .filter(Objects::nonNull)     // 过滤 null
            .map(String::trim)          // 去空格
            .filter(s -> !s.isEmpty())  // 过滤空串
            .distinct()                  // 去重
            .toList();
}
```

**代码解析：**

| 操作 | 为什么 |
|---|---|
| `getMenusByUserId` | 复用已有的四步链路（userId→roleIds→menuIds→menus），不重复写查询逻辑 |
| `map(Menu::getPerms)` | 从每个菜单提取 perms 字段，如 "system:user:add" |
| `filter(Objects::nonNull)` | 目录(M)没有 perms，值为 null，需要过滤掉 |
| `distinct()` | 防止多个角色分配了相同菜单导致权限重复 |

**为什么不需要建树？**
- `userTree()` 是给前端做动态路由的，需要嵌套结构
- `getPermissionsByUserId()` 是给 token 用的，只需要一个扁平的权限列表
- 建树是多余的操作，直接 flat map 就行

---

### 3.6 @PreAuthorize 方法级权限控制

```java
@RestController
@RequestMapping("/user")
public class UserController {

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<Page<User>> getUserPage(...) { ... }

    @PostMapping("/add")
    @PreAuthorize("hasAuthority('system:user:add')")
    Result<User> addUser(...) { ... }

    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result deleteById(...) { ... }
}
```

**代码解析：**

| 要点 | 说明 |
|---|---|
| `@PreAuthorize` | 在方法执行**之前**检查权限，不满足直接返回 403 |
| `hasAuthority('xxx')` | 检查 SecurityContext 中的权限列表是否包含 'xxx' |
| `@EnableMethodSecurity` | SecurityConfig 上加了这个注解，@PreAuthorize 才会生效 |
| **类级别 vs 方法级别** | 放在类上 = 所有方法用同一权限；放在方法上 = 每个方法独立控制 |

**权限校验完整链路：**
```
请求带 token → JwtFilter 解析 → 权限列表存入 SecurityContext
    → 请求到达 Controller 方法
    → @PreAuthorize 检查 SecurityContext 中是否有对应权限
    → 有 → 执行方法体 → 返回 200
    → 没有 → 抛出 AuthorizationDeniedException → 返回 403
```

---

## 四、数据库修复

### 4.1 sys_user_role 假 ID 问题

**问题：** 之前 `sys_user_role` 表里的数据用了假 ID：
```sql
INSERT INTO sys_user_role VALUES (1, 1001);   -- userId=1 不存在于 sys_user
INSERT INTO sys_user_role VALUES (2, 1002);   -- userId=2 不存在于 sys_user
```

**原因：** sys_user 表用的是雪花 ID（如 `1943123456789012001`），但 user_role 的数据还在用简单的 1、2。

**后果：** 登录时 `getRoleIdsByUserId(1943123456789012001)` 查不到任何角色 → permissions 为空 → token 里没权限 → @PreAuthorize 全部拒绝。

**修复：**
```sql
DELETE FROM sys_user_role;
INSERT INTO sys_user_role VALUES (1943123456789012001, 1001);  -- 张三 → 管理员
INSERT INTO sys_user_role VALUES (1943123456789012002, 1002);  -- 李四 → 普通用户
INSERT INTO sys_user_role VALUES (1943123456789012003, 1002);  -- 王五 → 普通用户
```

### 4.2 菜单按钮拆分

之前只有 5 个菜单、2 个按钮。现在拆为更细粒度的权限：

| 菜单 | perms | 类型 |
|---|---|---|
| 用户管理 | system:user:list | 菜单(C) |
| 用户查询 | system:user:query | 按钮(F) |
| 用户新增 | system:user:add | 按钮(F) |
| 用户删除 | system:user:delete | 按钮(F) |
| 角色管理 | system:role:list | 菜单(C) |
| 角色查询 | system:role:query | 按钮(F) |
| 角色新增 | system:role:add | 按钮(F) |
| 菜单管理 | system:menu:list | 菜单(C) |
| 菜单查询 | system:menu:query | 按钮(F) |

**角色权限分配：**
- 管理员（张三）：全部 9 个权限
- 普通用户（李四、王五）：系统管理目录 + 用户查询/新增 + 首页 = 5 个权限

### 4.3 完整 SQL 脚本

见 `demo/src/main/resources/sql/rbac_demo.sql`（库名 `rbac`）。

---

## 五、踩坑修复记录

### 5.1 JwtFilter 里 throw BusinessException

```java
// ❌ 错误：Filter 里抛异常
if (header == null || !header.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    throw new BusinessException(ResultCode.FORBIDDEN);  // 请求已经放行了，再抛异常无意义
}

// ✅ 正确：安静放行
if (header == null || !header.startsWith("Bearer ")) {
    filterChain.doFilter(request, response);
    return;  // 让 Security 自己判断
}
```

**原因：** Filter 的职责是"解析 token → 设置上下文"，不是"拒绝请求"。权限拒绝是 Security 框架的事。

### 5.2 "Bearer" vs "Bearer "

```java
// ❌ 错误：没有空格
header.startsWith("Bearer")   // "BearerToken123" 也会匹配

// ✅ 正确：有空格
header.startsWith("Bearer ")  // 只匹配 "Bearer xxx"
```

### 5.3 JwtFilter catch 块缺少 filterChain.doFilter

```java
// ❌ 错误：直接 return，请求挂住
catch (Exception e) {
    return;
}

// ✅ 正确：先放行再 return
catch (Exception e) {
    filterChain.doFilter(request, response);
    return;
}
```

### 5.4 sys_user_role 假 ID

```sql
-- ❌ 错误：userId 不匹配
INSERT INTO sys_user_role VALUES (1, 1001);

-- ✅ 正确：使用雪花 ID
INSERT INTO sys_user_role VALUES (1943123456789012001, 1001);
```

### 5.5 类级别 @PreAuthorize 太粗糙

```java
// ❌ 错误：类上统一一个权限，所有接口都只要 list 权限就能访问
@PreAuthorize("hasAuthority('system:user:list')")
@RestController
@RequestMapping("/user")
public class UserController { ... }

// ✅ 正确：每个方法独立权限
@RestController
@RequestMapping("/user")
public class UserController {
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<Page<User>> getUserPage(...) { ... }

    @PreAuthorize("hasAuthority('system:user:add')")
    Result<User> addUser(...) { ... }
}
```

---

## 六、RBAC 权限链路完整图

```
                        ┌─────────────────────┐
                        │   sys_user_role      │
                        │  userId → roleId      │
                        └──────────┬──────────┘
                                   │
┌──────────┐    ┌──────────────┐  │   ┌──────────────┐    ┌──────────┐
│  登录请求  │───→│ AuthService  │──┼──→│ sys_role_menu │───→│ sys_menu │
│ username  │    │  查用户      │  │   │ roleId→menuId │    │ 取 perms │
│ password  │    │  校验密码    │  │   └──────────────┘    └────┬─────┘
└──────────┘    └──────┬───────┘  │                             │
                       │          │                             │
                       ▼          │                             ▼
                ┌──────────────┐  │                    ┌──────────────┐
                │   JwtUtil    │  │                    │ permissions  │
                │ generateToken│◄─┘                    │ ["system:    │
                │ userId +      │                       │  user:list", │
                │ username +   │                       │  user:add", │
                │ permissions  │                       │  ...]        │
                └──────┬───────┘                       └──────────────┘
                       │
                       ▼
               ┌───────────────┐
               │  JWT Token    │
               │  Bearer xxx   │
               └───────┬───────┘
                       │
        ┌──────────────┘
        │
        ▼
┌───────────────────┐
│   后续请求        │
│  Authorization:   │
│  Bearer xxx       │
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│ JwtAuthentication │
│ Filter            │
│ ┌───────────────┐│
│ │ 解析 token     ││
│ │ 提取 userId    ││
│ │ 提取 permissions││
│ │ 构建 Auth 对象 ││
│ │ 存入            ││
│ │ SecurityContext││
│ └───────────────┘│
└─────────┬─────────┘
          │
          ▼
┌───────────────────┐
│ @PreAuthorize      │
│ hasAuthority(      │
│   "system:user:   │
│    add")           │
│                    │
│ 检查 SecurityContext│
│ 中的权限列表       │
│ ┌──────┐  ┌──────┐│
│ │ 有   │  │ 没有 ││
│ │ 200  │  │ 403  ││
│ └──────┘  └──────┘│
└───────────────────┘
```

---

## 七、Postman 使用指南

### 7.1 登录拿 token

```
POST /auth/login
Content-Type: x-www-form-urlencoded

username=zhangsan
password=123456

→ 响应返回 token 字符串
```

### 7.2 带 token 请求

```
GET /user/page?current=1&size=10

Headers:
  Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...

→ 正常返回数据
```

**注意：** `Bearer` 和 token 之间有一个空格。

### 7.3 权限验证

| 测试场景 | 预期结果 |
|---|---|
| 张三（管理员）访问 /user/page | 200 ✅（有 system:user:query） |
| 张三访问 /user/delete/{id} | 200 ✅（有 system:user:delete） |
| 李四（普通用户）访问 /user/page | 200 ✅（有 system:user:query） |
| 李四访问 /user/delete/{id} | 403 ❌（没有 system:user:delete） |
| 不带 token 访问 /user/page | 403 ❌（未认证） |
| 伪造 token 访问 /user/page | 403 ❌（token 解析失败） |
