# Spring Security + JWT 认证授权完整指南

> 对应项目进度：阶段 4（Spring Security + JWT）
> 建议学习时间：2-3 小时，理解原理后再动手写代码
> 本文档不包含实现代码，专注于"为什么"和"怎么做"

---

## 目录

- [第一章：为什么要认证和授权](#第一章为什么要认证和授权)
  - [1.1 没有 Security 的现状问题](#11-没有-security-的现状问题)
  - [1.2 认证 vs 授权](#12-认证-vs-授权)
  - [1.3 前后端分离下的认证方案选型](#13-前后端分离下的认证方案选型)
  - [1.4 为什么选 JWT 而不是 Session](#14-为什么选-jwt-而不是-session)
- [第二章：JWT 深入理解](#第二章-jwt-深入理解)
  - [2.1 JWT 的三段结构](#21-jwt-的三段结构)
  - [2.2 Payload 的标准声明 vs 自定义声明](#22-payload-的标准声明-vs-自定义声明)
  - [2.3 JWT 的生命周期](#23-jwt-的生命周期)
  - [2.4 Token 刷新机制（双 Token 方案）](#24-token-刷新机制双-token-方案)
  - [2.5 JWT 的安全边界](#25-jwt-的安全边界)
  - [2.6 jjwt 库 API 速查](#26-jjwt-库-api-速查)
- [第三章：Spring Security 核心原理](#第三章-spring-security-核心原理)
  - [3.1 Security 在 Spring Boot 中的位置](#31-security-在-spring-boot-中的位置)
  - [3.2 过滤器链（Filter Chain）](#32-过滤器链filter-chain)
  - [3.3 Security 的核心组件](#33-security-的核心组件)
  - [3.4 Security 的默认行为（不加配置时会发生什么）](#34-security-的默认行为不加配置时会发生什么)
- [第四章：认证流程——从登录到请求](#第四章认证流程从登录到请求)
  - [4.1 登录流程（认证）](#41-登录流程认证)
  - [4.2 后续请求流程（JWT 过滤器）](#42-后续请求流程jwt-过滤器)
  - [4.3 Security 上下文（SecurityContext）](#43-security-上下文securitycontext)
  - [4.4 用类比走一遍完整生命周期](#44-用类比走一遍完整生命周期)
- [第五章：授权流程——权限控制](#第五章授权流程权限控制)
  - [5.1 什么是"权限"](#51-什么是权限)
  - [5.2 权限在当前项目里的体现](#52-权限在当前项目里的体现)
  - [5.3 @PreAuthorize 注解原理](#53-preauthorize-注解原理)
  - [5.4 权限字符串的设计规范](#54-权限字符串的设计规范)
- [第六章：本项目 Security + JWT 的集成方案](#第六章本项目-security--jwt-的集成方案)
  - [6.1 整体架构图](#61-整体架构图)
  - [6.2 需要创建的类清单](#62-需要创建的类清单)
  - [6.3 需要引入的依赖](#63-需要引入的依赖)
  - [6.4 需要修改的现有代码](#64-需要修改的现有代码)
  - [6.5 请求放行路径清单](#65-请求放行路径清单)
- [第七章：安全注意事项与常见坑](#第七章安全注意事项与常见坑)
- [附录：预习自测题](#附录预习自测题)

---

# 第一章：为什么要认证和授权

## 1.1 没有 Security 的现状问题

你现在的项目里，任何人都可以直接调任何接口：

```
GET  /user/page          ← 无需登录就能看所有用户列表
POST /user/add           ← 无需登录就能新增用户
DELETE /user/delete/1    ← 无需登录就能删用户
GET  /menu/tree          ← 无需登录就能看菜单树
```

这在一个真正的企业系统里是不可接受的。你需要两道门：

```
第一道门：你是谁？（认证 / Authentication）
第二道门：你能做什么？（授权 / Authorization）
```

## 1.2 认证 vs 授权

这两个词经常被混着说，但它们是两件不同的事：

| | 认证（Authentication） | 授权（Authorization） |
|---|---|---|
| 回答的问题 | "你是谁？" | "你能做什么？" |
| 时机 | 登录时 | 登录后的每次请求时 |
| 类比 | 进大楼要刷门禁卡 | 进大楼后，普通员工卡只能进 1 楼，管理员卡能进所有楼层 |
| 本项目体现 | POST /auth/login → 校验用户名密码 → 返回 JWT Token | `@PreAuthorize("hasAuthority('system:user:add')")` 控制谁能调哪些接口 |

**先有认证，才有授权。** 不知道"你是谁"就无法判断"你能做什么"。

## 1.3 前后端分离下的认证方案选型

前后端分离项目（你的项目就是这种）常用的认证方案有三种：

| 方案 | 原理 | 优点 | 缺点 |
|---|---|---|---|
| **Cookie + Session** | 登录后服务端创建 Session，把 SessionID 写进 Cookie | 成熟稳定 | 前后端分离时跨域问题多；服务端要存 Session（占内存或存 Redis） |
| **JWT（无状态）** | 登录后服务端签发 Token，客户端每次请求带上 Token | 无状态——服务端不存任何东西；天然适合前后端分离；方便微服务间传递 | Token 无法主动失效（除非等过期或用黑名单） |
| **OAuth2 / OIDC** | 第三方认证（如"用微信登录"） | 安全性高 | 复杂，大多数内部管理系统用不到 |

**本项目选 JWT**，原因：
1. 前后端分离，JWT 天然适合
2. 无状态，当前是单体项目不需要共享 Session
3. 学习价值高——面试高频考点
4. 未来如果拆微服务，JWT 可以在服务间传递用户身份

## 1.4 为什么选 JWT 而不是 Session

用类比讲清楚两种方案的区别：

**Session 方案：**
```
你去酒店入住，前台给你一个房卡（SessionID），房卡号存在前台系统里（服务端 Session）。
每次你回房间，刷房卡，前台查系统确认这个房卡号有效。
如果你退房了，前台删除这个房卡号，房卡就失效了。

问题：前台系统要维护所有房卡号（服务端要存 Session）。
如果有 10 家连锁酒店（10 台服务器），每家的前台系统不互通——你在 A 酒店的房卡在 B 酒店刷不开。
```

**JWT 方案：**
```
你去酒店入住，前台给你一张手写的通行证（Token），上面写着"持证人：张三，有效期到周五"。
通行证上有酒店的公章（签名）。
每次你回房间，保安看通行证上的公章是真的 + 没过期，就放行。

保安不需要查任何系统（无状态）。
即使有 10 家连锁酒店，只要公章是真的，哪一家都认（天然适合分布式）。

问题：如果通行证被偷了，在过期之前，你没法让它立刻失效——除非你额外维护一个"作废通行证名单"（Token 黑名单）。
```

**对应到本项目：**
- Session 方案需要额外引入 Redis/数据库存 Session —— 增加复杂度
- JWT 方案服务端不存任何东西 —— 签名验证靠密钥，简单直接

---

# 第二章：JWT 深入理解

## 2.1 JWT 的三段结构

一个 JWT 长这样（中间有**两个点 `.`**）：

```
eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6ImFkbWluIiwiaWF0IjoxNjI4MDAwMDAwfQ.abc123def456
```

拆成三段：

```
第一段（Header）：   eyJhbGciOiJIUzI1NiJ9
第二段（Payload）：  eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6ImFkbWluIiwiaWF0IjoxNjI4MDAwMDAwfQ
第三段（Signature）： abc123def456
```

### 第一段：Header（头部）—— 算法声明

Base64 解码后：

```json
{
    "alg": "HS256",
    "typ": "JWT"
}
```

- `alg`：签名算法（HS256 = HMAC-SHA256）
- `typ`：固定 "JWT"

**公开的，任何人都能解码看到。** `Jwts.builder()` 会自动生成，不用手写。

### 第二段：Payload（载荷）—— 用户数据

Base64 解码后：

```json
{
    "sub": "1234567890",
    "name": "admin",
    "iat": 1628000000,
    "exp": 1628086400
}
```

这里是**你塞进去的用户信息**。

**⚠️ 重要：Base64 是编码，不是加密。** 任何人拿到 Token 都能 Base64 解码看到内容。所以**绝对不能在 Payload 里塞密码等敏感信息**。JWT 不负责"隐藏"数据，它负责的是"证明数据没被篡改"。

### 第三段：Signature（签名）—— 防篡改

生成方式：

```
HMACSHA256(
    "第一段.第二段",       ← 把 Header 和 Payload 用点拼起来
    "你的密钥 SecretKey"    ← 只有服务器知道
)
```

**用类比：** 你写了一封信（Payload），在封口滴了你独有的蜡封（Signature）。别人可以拆开看内容，但如果有人篡改了信的内容，蜡封就对不上了——因为签名是用"原始内容 + 密钥"算出来的。

**验证流程：**
```
收到 Token
  → 拆出前两段
  → 用服务器的密钥重新算签名
  → 跟第三段对比
  → 一致 → Token 没被篡改，信任 Payload 里的数据
  → 不一致 → Token 被篡改或伪造，拒绝
```

**JWT 的安全性完全取决于 Secret Key。** 只要密钥不泄露，Token 就没法伪造。

### 三段关系图

```
┌──────────────────────────────────────────────────────┐
│                      JWT Token                        │
│                                                        │
│  ┌──────────┐   ┌──────────────────────┐   ┌────────┐ │
│  │  Header   │ . │      Payload         │ . │  签名   │ │
│  │           │   │                      │   │        │ │
│  │ alg:HS256 │   │ sub: "1"             │   │ HMAC   │ │
│  │ typ: JWT  │   │ name: "admin"        │   │ (密钥) │ │
│  │           │   │ iat: 签发时间         │   │        │ │
│  │  公开     │   │ exp: 过期时间         │   │ 保密   │ │
│  └──────────┘   └──────────────────────┘   └────────┘ │
│   公开可解码        公开可解码                防篡改    │
└──────────────────────────────────────────────────────┘
```

## 2.2 Payload 的标准声明 vs 自定义声明

JWT 规范里定义了 7 个"标准声明"（Reserved Claims），字段名有固定含义：

| 标准声明 | 含义 | 是否必须 | 说明 |
|---|---|---|---|
| `iss`（Issuer） | 签发者 | 否 | 签发这个 Token 的是谁 |
| `sub`（Subject） | 主体 | 否 | Token 面向的用户，通常存 userId |
| `aud`（Audience） | 接收者 | 否 | 预期谁来用这个 Token |
| `exp`（Expiration） | 过期时间 | **建议** | 过了时间戳 Token 失效 |
| `nbf`（Not Before） | 生效时间 | 否 | 在此时间之前 Token 无效 |
| `iat`（Issued At） | 签发时间 | 否 | Token 什么时候生成的 |
| `jti`（JWT ID） | 唯一标识 | 否 | Token 的唯一编号 |

**自定义声明**就是你自己的业务数据，随便起名：

```json
{
    "sub": 1,                    // 标准：用户 ID
    "name": "admin",            // 自定义：用户名
    "permissions": ["system:user:add", "system:user:list"]  // 自定义：权限列表
}
```

**本项目建议 Payload 里塞：**
- `sub`：userId
- `username`：用户名
- `permissions`：权限字符串列表（从用户角色对应的菜单 perms 字段提取）

这样校验 Token 后就能直接知道"谁 + 能做什么"，不需要再查数据库。

## 2.3 JWT 的生命周期

```
1. 用户提交用户名密码
       │
       ▼
2. 服务端校验用户名密码（查数据库）
       │  校验通过
       ▼
3. 生成 JWT（把 userId/username/permissions 塞进 Payload，用密钥签名）
       │
       ▼
4. 返回 Token 给前端（JSON 格式：{"code":200, "data":{"token":"xxx"}}）
       │
       ▼
5. 前端保存 Token（通常存 localStorage 或 sessionStorage）
       │
       ▼
6. 之后每次请求，前端在请求头里带上 Token：
   Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
       │
       ▼
7. 服务端的 JWT 过滤器拦截请求，取出 Token → 校验签名 + 检查过期 → 解析出用户信息
       │  校验通过
       ▼
8. 把用户信息存入 SecurityContext（方便后续权限校验使用）
       │
       ▼
9. 请求到达 Controller 正常处理
       │
       ▼
10. Token 过期 → 401 → 前端跳转到登录页
```

## 2.4 Token 刷新机制（双 Token 方案）

Access Token 的有效期不能太长——如果 Token 被偷了，有效期越长，被盗用的时间窗口越大。通常设 **2 小时**。

但如果每 2 小时就让用户重新登录，体验很差。所以用**双 Token 方案**：

| Token | 有效期 | 用途 |
|---|---|---|
| **Access Token** | 短（2 小时） | 每次请求都带，JWT 过滤器校验它 |
| **Refresh Token** | 长（7 天） | 只在 Access Token 过期时用来"换新" |

流程：

```
Access Token 过期 → 前端收到 401
  → 前端带上 Refresh Token 请求 /auth/refresh
  → 服务端校验 Refresh Token（签名 + 过期时间）
  → 校验通过 → 生成新的 Access Token 返回
  → 前端用新 Access Token 继续请求
```

**Refresh Token 不需要频繁校验，只在"换新"时才查一次。** 这样用户 7 天内不用重新登录，但 Access Token 一直保持短期。

**本项目计划：** 先做单 Token（Access Token only），跑通认证流程后，再考虑加 Refresh Token。

## 2.5 JWT 的安全边界

| 场景 | 安全吗 | 说明 |
|---|---|---|
| Payload 被 Base64 解码看到 | ✅ 安全（设计如此） | JWT 不隐藏数据，只防篡改。敏感信息不要塞进 Payload |
| Secret Key 泄露 | ❌ 不安全 | 密钥泄露 = 任何人都能伪造 Token |
| Token 被 XSS 攻击偷走 | ❌ 不安全 | Token 存 localStorage 的话，XSS 脚本能读走。更安全的做法是存 HttpOnly Cookie |
| Token 没有 exp 过期时间 | ❌ 不安全 | Token 永不过期 = 被盗用后永远有效 |
| Token 过期时间太长 | ⚠️ 有风险 | 建议不超过 2 小时 |
| 用 HTTPS 传输 | ✅ 必须的 | HTTP 明文传输 Token 会被中间人截获 |

## 2.6 jjwt 库 API 速查

本项目将使用 `jjwt` 库（你之前用过）。核心 API：

### 生成 Token

```java
// 1. 准备密钥
SecretKey key = Keys.hmacShaKeyFor("你的密钥至少256位".getBytes(StandardCharsets.UTF_8));

// 2. 构建 Payload
Map<String, Object> claims = new HashMap<>();
claims.put("sub", userId);
claims.put("username", username);

// 3. 生成 Token
String token = Jwts.builder()
    .setClaims(claims)                       // 写入自定义声明
    .setExpiration(new Date(expireTime))    // 设置过期时间
    .signWith(key, SignatureAlgorithm.HS256) // 签名
    .compact();                              // 拼成最终字符串
```

### 校验 + 解析 Token

```java
Claims claims = Jwts.parserBuilder()
    .setSigningKey(key)     // 用服务器的密钥
    .build()
    .parseClaimsJws(token)   // 校验签名 + 检查过期（失败抛异常）
    .getBody();              // 返回 Payload

Long userId = claims.get("sub", Long.class);
String username = claims.get("username", String.class);
```

### 异常

| 异常 | 含义 | HTTP 状态码 |
|---|---|---|
| `ExpiredJwtException` | Token 过期了 | 401 |
| `SignatureException` | 签名不对（Token 被篡改或伪造） | 401 |
| `MalformedJwtException` | Token 格式错误 | 401 |

**这三个异常都应该被全局异常处理器捕获，返回统一的 401 响应。**

---

# 第三章：Spring Security 核心原理

## 3.1 Security 在 Spring Boot 中的位置

你现在的请求流程：

```
浏览器 → Tomcat → DispatcherServlet → Controller
```

加了 Security 之后：

```
浏览器 → Tomcat → DispatcherServlet → Security 过滤器链 → Controller
                                      （一层层检查）
```

Security 的过滤器是**在 DispatcherServlet 之前**执行的。也就是说，请求还没到 Controller 就已经被 Security 拦截了。

## 3.2 过滤器链（Filter Chain）

Security 的核心就是一个**过滤器链**——请求进来后，依次经过每个过滤器，每个过滤器做各自的检查：

```
HTTP 请求
  │
  ▼
┌─────────────────────────────────────────────────────┐
│  Security Filter Chain（安全过滤器链）                │
│                                                       │
│  ┌─────────┐  ┌─────────┐  ┌──────────┐  ┌──────┐ │
│  │ Filter1  │→ │ Filter2  │→ │ Filter3   │→ │ ...  │ │
│  │(编码/   │  │(Session  │  │(CSRF     │  │      │ │
│  │ 字符集) │  │ 管理)    │  │ 防护)    │  │      │ │
│  └─────────┘  └─────────┘  └──────────┘  └──────┘ │
└───────────────────────────┬─────────────────────────┘
                            │
                            ▼
                      Controller
```

**你要做的事情是：在链里插入一个你自己写的 JWT 过滤器。**

```
HTTP 请求
  │
  ▼
┌─────────────────────────────────────────────────────┐
│  Security Filter Chain                               │
│                                                       │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐          │
│  │ 其余     │→ │ 其余     │→ │ JWT 过滤器│ → Controller│
│  │ Filter   │  │ Filter   │  │ (你写的) │          │
│  └──────────┘  └──────────┘  └──────────┘          │
└─────────────────────────────────────────────────────┘
```

**JWT 过滤器做的事情：**
1. 从请求头的 `Authorization` 字段里取出 Token
2. 校验 Token（签名 + 过期时间）
3. 解析出用户信息（userId / username / permissions）
4. 把用户信息存入 Security 上下文（后续 @PreAuthorize 会从这里取）
5. 放行请求到 Controller

如果 Token 无效或缺失 → 返回 401，请求不会到达 Controller。

## 3.3 Security 的核心组件

| 组件 | 英文 | 职责 | 类比 |
|---|---|---|---|
| 用户信息 | `UserDetails` | 存储当前登录用户的用户名、密码、权限列表 | 用户的身份证 |
| 用户服务 | `UserDetailsService` | 从数据库加载用户信息（实现 `loadUserByUsername` 方法） | 去数据库查用户的"查号台" |
| 认证令牌 | `Authentication` | 一次认证结果（谁、密码对不对、有哪些权限） | 一次"身份核验结果" |
| 安全上下文 | `SecurityContext` | 存放当前线程的 `Authentication` | 用户的"临时通行证" |
| 安全上下文持有者 | `SecurityContextHolder` | 管理 `SecurityContext`（本质是 ThreadLocal） | 通行证收纳盒 |

**关键理解点：** Security 的所有组件形成一条链：

```
1. 请求进来 → JWT 过滤器从 Token 解析出 userId
2. userId → UserDetailsService.loadUserByUsername → 查数据库拿用户信息（密码、权限）
3. 用户信息 → 构建 Authentication 对象
4. Authentication → 存入 SecurityContextHolder
5. Controller 里或 @PreAuthorize 里 → 从 SecurityContextHolder 取 Authentication → 做权限判断
```

**但本项目有优化空间：** 因为 JWT 的 Payload 里已经存了 permissions（权限列表），如果每次请求都走 `UserDetailsService` 查数据库，就白存了。所以我们的 JWT 过滤器可以直接从 Token 里解析出权限，不走 `UserDetailsService`——这就是"无状态认证"的核心优势。

## 3.4 Security 的默认行为（不加配置时会发生什么）

如果你只引入 `spring-boot-starter-security` 依赖，不加任何配置，启动应用后会发生什么？

**所有接口都需要认证，且有一个默认登录表单。**

```
访问 GET /user/page
  → 自动跳转到 /login（Spring Security 自带的丑登录页）
  → 默认用户名：user，密码：启动时打印在控制台
  → 登录后才能访问接口
```

这个默认行为对企业项目毫无用处。我们要做的第一步就是**覆盖这些默认行为**——用自己的 JWT 过滤器替换默认的 Session 登录方式，自定义哪些路径放行、哪些需要认证。

---

# 第四章：认证流程——从登录到请求

## 4.1 登录流程（认证）

```
前端 POST /auth/login（表单提交：username + password）
       │
       ▼
AuthController.login(username, password)
       │
       ▼
UserService.login(username, password)
       │
       ▼
步骤1：查数据库，验证用户名密码
       │  密码用 BCryptPasswordEncoder.matches(rawPassword, encodedPassword) 校验
       │  ❌ 用户不存在或密码错误 → 抛 BusinessException → 全局异常处理器返回非200
       │  ✅ 校验通过
       ▼
步骤2：查该用户的权限列表
       │  userId → roleIds → menuIds → sys_menu(perms字段) → 权限字符串列表
       │  例如：["system:user:list", "system:user:add", "system:role:list"]
       ▼
步骤3：生成 JWT
       │  Payload 塞入：sub=userId, username, permissions=权限列表
       │  exp=2小时后
       ▼
步骤4：返回 Token
       │  Result.success(token)
       ▼
前端收到 Token，保存到 localStorage
```

**关键点：登录接口本身必须放行（不需要 Token 就能访问），否则用户没法登录。**

## 4.2 后续请求流程（JWT 过滤器）

```
前端 GET /user/page（请求头带 Authorization: Bearer eyJ...）
       │
       ▼
Spring Security 过滤器链
       │
       ▼
JWT 过滤器（你写的 JwtAuthenticationFilter）
       │
       ▼
步骤1：从请求头取出 Token
       │  String header = request.getHeader("Authorization");
       │  token = header.substring(7);  // 去掉 "Bearer " 前缀
       │  ❌ 没有 Authorization 头 → 放行（让后续过滤器处理 → 最终 403/401）
       ▼
步骤2：校验 Token
       │  Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
       │  ❌ 过期/签名不对 → 抛异常 → 全局异常处理器返回 401
       │  ✅ 校验通过
       ▼
步骤3：解析用户信息
       │  Claims claims = parseJWT.getBody();
       │  Long userId = claims.get("sub", Long.class);
       │  String username = claims.get("username", String.class);
       │  List<String> permissions = claims.get("permissions", List.class);
       ▼
步骤4：构建 Authentication 并存入 SecurityContext
       │  Authentication auth = new UsernamePasswordAuthenticationToken(
       │      principal,    // userId 或 UserDetails
       │      null,         // credentials（已验证过，不需要密码了）
       │      authorities   // 权限列表
       │  );
       │  SecurityContextHolder.getContext().setAuthentication(auth);
       ▼
步骤5：放行
       │  filterChain.doFilter(request, response);
       ▼
到达 Controller
       │
       ▼
如果 Controller 方法上有 @PreAuthorize("hasAuthority('system:user:list')")
       │  → 从 SecurityContext 取 Authentication → 检查权限列表里有没有这个字符串
       │  ✅ 有 → 正常执行
       │  ❌ 没有 → 403 Forbidden
```

## 4.3 Security 上下文（SecurityContext）

`SecurityContextHolder` 是 Security 存放"当前用户是谁"的地方。它底层用 `ThreadLocal`，所以**每个请求线程有自己独立的上下文**，互不干扰。

```java
// 存入（JWT 过滤器里做）
SecurityContextHolder.getContext().setAuthentication(authentication);

// 取出（Controller / @PreAuthorize 里自动做）
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();
Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();
```

**类比：** SecurityContext 就是"当前线程的口袋"。JWT 过滤器把"用户通行证"塞进去，后续的 @PreAuthorize 从口袋里掏出来检查。

**请求结束后，口袋会被自动清空。** 下一个请求进来，JWT 过滤器再重新塞。

## 4.4 用类比走一遍完整生命周期

```
你（前端）去酒店（后端系统）：

1. 登记（登录）：
   你在前台（/auth/login）出示身份证（用户名+密码）
   前台核实你身份证是真的 → 给你一张通行证（JWT Token）
   通行证上盖了酒店的公章（Signature），写着你的名字和允许进入的区域（Payload）

2. 进入房间（后续请求）：
   你每进一个门，保安（JWT 过滤器）检查你的通行证
   - 公章是真的 → 确认你是酒店认可的客人 → 在保安的记录本上记下你的身份（SecurityContext）→ 放行
   - 公章是假的或过期 → 拒绝进入（401）

3. 进入特定房间（权限控制）：
   某些房间门口有额外保安（@PreAuthorize），检查你通行证上写的"允许进入区域"
   - 写了"可以进 A 区" → 放行
   - 没写"可以进 B 区" → 拒绝（403）

4. 离开（请求结束）：
   保安的记录本自动翻到下一页（ThreadLocal 清空）
   下一个人来，重新走一遍流程
```

---

# 第五章：授权流程——权限控制

## 5.1 什么是"权限"

在你的 RBAC 系统里，**权限就是一个字符串**，存在 `sys_menu` 表的 `perms` 字段里：

```
system:user:list      ← 用户列表的查看权限
system:user:add       ← 用户新增的权限
system:user:edit      ← 用户编辑的权限
system:user:delete    ← 用户删除的权限
system:role:list      ← 角色列表的查看权限
```

权限字符串的设计规范：`模块:资源:操作`

- `system`：模块名（系统管理）
- `user`：资源名（用户）
- `list`/`add`/`edit`/`delete`：操作名

这种 `:` 分隔的三段式是业界标准（若依也用这种）。

## 5.2 权限在当前项目里的体现

你数据库里已经有一部分了（`sys_menu.perms` 字段）：

```
id=2002  perms="system:user:list"   ← 用户管理菜单的查看权限
id=2003  perms="system:user:add"    ← 用户新增按钮的权限
id=2004  perms="system:role:list"   ← 角色管理菜单的查看权限
```

**流程：**

```
用户登录
  → userId → roleIds → menuIds → sys_menu
  → 提取所有 perms 字段 → ["system:user:list", "system:user:add", "system:role:list"]
  → 塞进 JWT Payload
  → 每次请求时从 Token 里解析出来
  → @PreAuthorize 检查权限列表里有没有对应的字符串
```

## 5.3 @PreAuthorize 注解原理

`@PreAuthorize` 是一个方法级注解，放在 Controller 方法上：

```java
@GetMapping("/page")
@PreAuthorize("hasAuthority('system:user:list')")   // 有这个权限才能访问
public Result<?> page(...) { ... }

@PostMapping("/add")
@PreAuthorize("hasAuthority('system:user:add')")   // 有这个权限才能访问
public Result<?> add(...) { ... }
```

**工作原理：**

1. 请求到达 Controller 方法前，Spring AOP 拦截
2. 解析 `@PreAuthorize` 里的 SpEL 表达式（`hasAuthority('xxx')`）
3. 从 `SecurityContextHolder` 取出当前用户的权限列表
4. 检查权限列表里有没有 `system:user:list` 这个字符串
5. 有 → 执行方法；没有 → 抛 `AccessDeniedException` → 全局异常处理器返回 403

**常见 SpEL 表达式：**

| 表达式 | 含义 |
|---|---|
| `hasAuthority('system:user:add')` | 有指定权限字符串 |
| `hasAnyAuthority('system:user:add', 'system:user:edit')` | 有任意一个权限 |
| `hasRole('admin')` | 有指定角色（前缀自动加 `ROLE_`） |
| `isAuthenticated()` | 已登录（不是匿名用户） |
| `permitAll()` | 允许所有人（通常不写，直接不放 @PreAuthorize） |

**本项目主要用 `hasAuthority`。** 因为我们的权限是"按钮级"的（`system:user:add` 这种细粒度权限），不是"角色级"的。

## 5.4 权限字符串的设计规范

| 规范 | 示例 | 说明 |
|---|---|---|
| `模块:资源:操作` | `system:user:add` | 三段式，业界标准 |
| 操作名用动词 | `add`、`edit`、`delete`、`list`、`export` | 直观 |
| 不需要权限的地方不填 | 目录(M)、无操作权限的菜单 → `perms` 字段为 `NULL` | 只有有"权限控制意义"的地方才填 |

---

# 第六章：本项目 Security + JWT 的集成方案

## 6.1 整体架构图

```
┌──────────────┐     ┌──────────────────────────────────────────────────┐
│    前端       │     │              后端 (Spring Boot)                  │
│              │     │                                                  │
│  localStorage│     │  ┌────────────────────────────────────────────┐  │
│  存 Token    │     │  │  Security 过滤器链                         │  │
│              │     │  │                                            │  │
│  每次请求带  │     │  │  /auth/login  → 放行（不需要 Token）       │  │
│  Authorization│     │  │  /doc.html    → 放行（Knife4j 文档）       │  │
│  请求头      │     │  │  /druid/*     → 放行（Druid 监控）         │  │
│              │     │  │                                            │  │
│              │     │  │  其他所有请求 → JWT 过滤器                   │  │
│              │     │  │    └─ 取 Token → 校验 → 解析用户信息       │  │
│              │     │  │    └─ 存入 SecurityContext                  │  │
│              │     │  │    └─ 放行到 Controller                     │  │
└──────┬───────┘     │  └────────────────────────────────────────────┘  │
       │             │                      │                           │
       │  HTTP       │                      ▼                           │
       │  请求       │              Controller                           │
       │             │                @PreAuthorize                     │
       │             │                → 从 SecurityContext 取权限         │
       │             │                → 有权限 → 执行                     │
       │             │                → 无权限 → 403                     │
       ▼             └──────────────────────────────────────────────────┘
```

## 6.2 需要创建的类清单

| 类 | 包路径 | 职责 |
|---|---|---|
| `JwtUtil` | `cheer.demo.common.utils` | JWT 生成 + 校验 + 解析的工具类 |
| `SecurityConfig` | `cheer.demo.common.config` | Security 配置类：配置过滤器链、放行路径 |
| `JwtAuthenticationFilter` | `cheer.demo.common.filter` | JWT 过滤器：从请求头取 Token → 校验 → 存 SecurityContext |
| `LoginController` | `cheer.demo.controller` | 登录接口 `POST /auth/login` |
| `AuthService` / `AuthServiceImpl` | `cheer.demo.service` | 登录业务逻辑（查用户、校验密码、查权限、生成 Token） |

## 6.3 需要引入的依赖

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- jjwt（JWT 库） -->
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

**注意：** 你之前用的 `io.jsonwebtoken:jjwt` 是旧版写法（0.9.x），新版 0.12.x 拆成了三个 artifact（api / impl / jackson）。建议用新版，API 更清晰。

## 6.4 需要修改的现有代码

| 现有代码 | 修改内容 |
|---|---|
| `GlobalExceptionHandler` | 新增 JWT 异常处理（ExpiredJwtException → 401） |
| `UserService` / `UserServiceImpl` | 新增 `loadUserByUsername` 方法（或直接在 AuthService 里查用户） |
| `ResultCode` | 确认 `UNAUTHORIZED(401)` 和 `FORBIDDEN(403)` 已有（已有 ✅） |
| `pom.xml` | 添加 Security + jjwt 依赖 |
| `application.yml` | 添加 JWT 密钥和过期时间配置 |

## 6.5 请求放行路径清单

有些路径不需要认证就能访问：

```java
http.permitRequest()                        // 放行以下路径
    .requestMatchers("/auth/login").permitAll()    // 登录接口
    .requestMatchers("/doc.html").permitAll()      // Knife4j 文档页
    .requestMatchers("/webjars/**").permitAll()     // Knife4j 静态资源
    .requestMatchers("/swagger-resources/**").permitAll()
    .requestMatchers("/v3/api-docs/**").permitAll()
    .requestMatchers("/druid/**").permitAll()       // Druid 监控
    .anyRequest().authenticated();                  // 其他所有请求需要认证
```

**注意：** `permitAll()` = 完全放行，不需要 Token 也不需要任何认证。`authenticated()` = 必须经过认证（即必须带有效 Token）。

---

# 第七章：安全注意事项与常见坑

## 7.1 密码存储

**❌ 绝对不能明文存密码。**
**❌ 不能用简单的 MD5（MD5 已被彩虹表破解）。**

**✅ 用 BCrypt。** Spring Security 自带 `BCryptPasswordEncoder`：

```java
// 注册/新增用户时加密
String encodedPassword = new BCryptPasswordEncoder().encode(rawPassword);
user.setPassword(encodedPassword);

// 登录时校验
boolean match = new BCryptPasswordEncoder().matches(rawPassword, encodedPassword);
```

BCrypt 的特点：
- 每次加密同一个密码，结果都不一样（内部加了随机盐值）
- 不需要你手动管理盐值
- 可调节"强度"（默认 10 轮，越大越慢越安全）

## 7.2 BCrypt 与当前项目

你现在的用户密码怎么存的？去看看 `UserServiceImpl.add()` 和你的测试数据——如果密码是明文或简单 MD5，接 Security 之前要改成 BCrypt。

## 7.3 JWT 密钥的要求

HS256 算法要求密钥**至少 256 位（32 个字符）**。太短的密钥会报错：

```
The key must be at least 256 bits (32 characters)
```

所以密钥不能是 `"my-secret"` 这种——要用随机长字符串，比如：

```
mySecretKeyForJWTTokenGenerationThatIsAtLeast32Chars!
```

**这个密钥要写死在配置文件里（`application.yml`），不要硬编码在 Java 代码中。**

## 7.4 常见坑汇总

| 坑 | 现象 | 原因 | 修复 |
|---|---|---|---|
| 引入 Security 后所有接口返回 401 | Postman 调任何接口都是 401 | Security 默认所有路径需要认证 | 配置 `SecurityFilterChain` 放行路径 |
| JWT 过滤器顺序不对 | 有 Token 但仍然 401 | 过滤器要在 `UsernamePasswordAuthenticationFilter` 之前执行 | `addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)` |
| Token 校验抛异常但不被全局处理器捕获 | 返回 500 而不是 401 | JWT 异常在 Filter 里抛出，`@RestControllerAdvice` 捕获不到 | 在 Filter 里 catch JWT 异常，手动写 401 响应 |
| `@PreAuthorize` 不生效 | 接口不需要权限就能访问 | Security 配置里没开启方法级权限控制 | `@EnableMethodSecurity` 注解 |
| 密码校验不过 | 登录总是密码错误 | 数据库存的是明文，但校验时用 BCrypt.matches | 存密码时用 BCrypt.encode，查密码时用 BCrypt.matches |
| SecurityContext 取到的是匿名用户 | @PreAuthorize 总是 403 | JWT 过滤器没正确存入 Authentication | 检查 SecurityContextHolder.getContext().setAuthentication(auth) 是否调了 |

---

# 附录：预习自测题

## 选择题

**1.** JWT 的三段分别是？
- A. Header / Body / Footer
- B. Header / Payload / Signature
- C. Key / Value / Token

**2.** JWT 的 Payload 被 Base64 解码后，里面的数据是？
- A. 加密的，只有服务端能看
- B. 编码的，任何人都能解码看到
- C. 压缩的，需要解压

**3.** JWT 的安全性靠什么保证？
- A. Payload 加密
- B. 第三段 Signature 的签名验证
- C. HTTPS 传输

**4.** Spring Security 的过滤器链在什么位置执行？
- A. Controller 之后
- B. DispatcherServlet 之前
- C. Controller 之前、DispatcherServlet 之后

**5.** `@PreAuthorize("hasAuthority('system:user:add')")` 检查的权限列表从哪来？
- A. 数据库
- B. SecurityContext（由 JWT 过滤器存入）
- C. 配置文件

## 思考题

**6.** 为什么 JWT 过滤器要在 `UsernamePasswordAuthenticationFilter` 之前执行？（提示：`UsernamePasswordAuthenticationFilter` 是 Security 默认的"表单登录"过滤器，它会尝试从请求里取 `username` 和 `password` 参数做登录。如果不把 JWT 过滤器放在它前面，会怎样？）

**7.** 如果用户的 Token 被别人偷走了，在你没有 Token 黑名单机制的情况下，这个 Token 多久内都能被冒用？你怎么降低这个风险？

**8.** 登录接口 `POST /auth/login` 为什么要配置为 `permitAll()` 放行？如果配成 `authenticated()` 会发生什么？

---

## 答案

1. **B**。Header（算法声明）/ Payload（用户数据）/ Signature（签名防篡改）。

2. **B**。Base64 是编码不是加密，任何人都能解码看到内容。所以不能在 Payload 里塞密码。

3. **B**。安全性靠第三段 Signature——用密钥对 Header.Payload 做签名，验证时重新算签名对比。HTTPS 只是传输层加密，防止中间人截获，但不防 Token 伪造。

4. **B**。Security 的过滤器是 Servlet Filter，在 DispatcherServlet 之前执行。也就是说请求还没到 Spring MVC 的 Controller 就已经被 Security 拦截了。

5. **B**。JWT 过滤器从 Token 里解析出权限列表，构建 Authentication 对象存入 SecurityContext。@PreAuthorize 从 SecurityContext 取 Authentication 再检查权限。整个链路是：Token → JWT 过滤器 → SecurityContext → @PreAuthorize。

6. **如果不把 JWT 过滤器放在 `UsernamePasswordAuthenticationFilter` 前面，默认的表单登录过滤器会先执行——它尝试从请求参数里取 username/password，取不到就认为认证失败，直接返回 401。JWT 过滤器根本没机会执行。所以要把 JWT 过滤器加在链的前面，让它先处理 Token 认证。**

7. **Token 被偷后，在过期时间内（通常 2 小时）都能被冒用。降低风险的方式：① Access Token 有效期设短（2 小时）；② 使用 HTTPS 防止中间人截获；③ 后续加 Token 黑名单（Redis 存储，退出登录时把 Token 加入黑名单）。**

8. **登录接口是用户获取 Token 的唯一入口。如果配成 `authenticated()`，意味着"要访问登录接口必须先有有效 Token"——但用户还没有 Token 就没法登录，形成了死循环（先有鸡还是先有蛋）。所以登录接口必须放行。**
