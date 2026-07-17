# 今日总结（2026-07-10）— Stage 5：Redis 缓存（进行中）

> 本文档记录 Stage 5 Redis 缓存的完整实现过程，包含方案选型、踩坑排查、最终解决方案。

---

## 一、今日目标

在已有的 RBAC + JWT 系统上引入 Redis 缓存，减少数据库查询压力：

```
之前：每次请求 → 查 3~5 张 MySQL 表 → 返回
之后：第一次查库写缓存，后续直接读 Redis（微秒级响应）
```

**当前完成进度：** Redis 基础集成 + 菜单树缓存（已完成），Redisson 分布式锁（待做）

---

## 二、前置准备

### 2.1 启动 Redis 服务

通过 WSL 或 Docker 安装并启动 Redis，监听 6379 端口。后台运行方式：

```bash
# WSL
redis-server --daemonize yes

# Docker
docker run -d --name redis --restart=always -p 6379:6379 redis:7
```

### 2.2 pom.xml 新增依赖

```xml
<!-- Spring Data Redis（含 Lettuce 客户端） -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- 连接池 -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

**Lettuce vs Jedis：**
- Spring Boot 默认用 Lettuce（基于 Netty，异步非阻塞，线程安全，一个连接池就够了）
- Jedis 是传统阻塞式，每个线程需要独立连接
- 选 Lettuce，不需要额外引入

### 2.3 application.yml 新增 Redis 配置

```yaml
spring:
  data:
    redis:
      host: localhost
      port: 6379
      password:        # 默认无密码
      database: 0       # 0~15 共 16 个库
      lettuce:
        pool:
          max-active: 8      # 最大连接数
          max-idle: 8        # 最大空闲连接
          min-idle: 0        # 最小空闲连接
          max-wait: -1ms     # 获取连接最大等待时间
      timeout: 3000ms        # 连接超时时间
```

---

## 三、RedisConfig — 序列化配置（踩坑重点）

### 3.1 为什么需要自定义序列化？

Spring Boot 自动配置的 `RedisTemplate` 默认用 JDK 序列化：
- Key 和 Value 都变成二进制
- 在 RESP（Redis 可视化工具）里看到的是乱码，无法调试
- 需要改成 Key 用 String、Value 用 JSON

### 3.2 方案对比（踩坑记录）

我们经历了 **三次尝试**才找到正确的方案：

#### 方案一：`RedisSerializer.json()`（默认 GenericJackson2JsonRedisSerializer）

```java
template.setValueSerializer(RedisSerializer.json());
```

**失败原因：** 反序列化嵌套对象时，内层的 `List<MenuTreeVO>` 会被还原成 `LinkedHashMap`，不是 `MenuTreeVO`，强转报 `ClassCastException`。

```
存入：List<MenuTreeVO>（children 里有 List<MenuTreeVO>）
取出：List<LinkedHashMap>（children 里也是 LinkedHashMap）
强转：(List<MenuTreeVO>) cached → ClassCastException!
```

**同时 `List<String>` 也有问题：**
```
存入：["system:user:list", "system:user:add"]
取出：["system:user:list", "system:user:add"]  ← 看起来没问题
但实际类型是 ArrayList<LinkedHashMap>，不是 ArrayList<String>
```

#### 方案二：自定义 ObjectMapper + `activateDefaultTyping(OBJECT_AND_NON_CONCRETE)`

```java
ObjectMapper objectMapper = new ObjectMapper();
objectMapper.activateDefaultTyping(
    LaissezFaireSubTypeValidator.instance,
    ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,  // ← 问题在这里
    JsonTypeInfo.As.PROPERTY
);
```

**失败原因：** `OBJECT_AND_NON_CONCRETE` 策略会给**所有非具体类型**加 `@class` 类型标记，包括 `String`。当 `List<String>` 存入 Redis 后：

```json
["java.lang.String", "system:user:list", "java.lang.String", "system:user:add"]
```

取出时 Jackson 把 `"system:user:list"` 当作类名去加载 → 报错：

```
Could not resolve type id 'system:user:list' as a subtype of `java.lang.Object`: no such class found
```

#### 方案三（最终）：`StringRedisTemplate` + `ObjectMapper` + `TypeReference`

**彻底绕开 Spring 的自动类型推断。** 用 `StringRedisTemplate` 只存纯字符串，手动用 Jackson 的 `ObjectMapper` 做序列化/反序列化，并通过 `TypeReference` 明确指定目标类型。

```java
// 存：Java 对象 → JSON 字符串 → Redis
stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(data), ttl);

// 取：Redis → JSON 字符串 → Java 对象（明确指定类型）
String json = stringRedisTemplate.opsForValue().get(key);
List<Menu> menus = objectMapper.readValue(json, new TypeReference<List<Menu>>() {});
```

**为什么这个方案能行：**

| 对比项 | 方案一/二 | 方案三（最终） |
|---|---|---|
| 存储格式 | Redis 自动决定类型信息 | 纯 JSON 字符串 |
| 反序列化 | Spring 自动推断（猜错） | `TypeReference` 明确指定（不会错） |
| 嵌套对象 | 内层变成 LinkedHashMap | 正确还原为 MenuTreeVO |
| `List<String>` | 被当成类名 | 正确还原为 String 列表 |
| RESP 可读性 | 方案二有 @class 杂讯 | 纯 JSON，完全可读 |

### 3.3 最终的 RedisConfig

```java
@Configuration
public class RedisConfig {
    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.setVisibility(PropertyAccessor.ALL, JsonAutoDetect.Visibility.ANY);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        objectMapper.registerModule(new JavaTimeModule());
        Jackson2JsonRedisSerializer<Object> serializer =
            new Jackson2JsonRedisSerializer<>(objectMapper, Object.class);
        template.setKeySerializer(RedisSerializer.string());
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(serializer);
        template.afterPropertiesSet();
        return template;
    }
}
```

**注意：** RedisConfig 保留了自定义 ObjectMapper 配置（其他场景可能会用到 `RedisTemplate<String, Object>`），但 `MenuServiceImpl` 实际使用的是 `StringRedisTemplate`。

---

## 四、菜单树缓存实现

### 4.1 缓存设计

| 缓存方法 | Key | Value | TTL |
|---|---|---|---|
| `tree()` | `menu:tree:all` | `List<Menu>` 的 JSON 字符串 | 30 分钟 |
| `userTree(userId)` | `user:menuTree:{userId}` | `List<MenuTreeVO>` 的 JSON 字符串 | 30 分钟 |
| `getPermissionsByUserId(userId)` | `user:perms:{userId}` | `List<String>` 的 JSON 字符串 | 30 分钟 |

**为什么缓存 flat 的 `List<Menu>` 而不是树？**

`tree()` 方法选择缓存 flat 的 `List<Menu>`（所有菜单的平铺列表），取出后在内存中调用 `buildTree()` 构建树。原因：
1. `List<Menu>` 是简单对象的 List，没有嵌套，序列化/反序列化稳定
2. `buildTree()` 在内存中极快（微秒级），不影响性能
3. 避免了 `List<MenuTreeVO>`（含嵌套 children）的序列化问题

但 `userTree()` 直接缓存了 `List<MenuTreeVO>`，因为用 `StringRedisTemplate` + `TypeReference` 方案后，嵌套对象也能正确反序列化了。

### 4.2 完整代码

```java
@Slf4j
@Service
public class MenuServiceImpl extends ServiceImpl<MenuMapper, Menu> implements MenuService {
    @Autowired
    private MenuMapper menuMapper;
    @Autowired
    private UserRoleMapper userRoleMapper;
    @Autowired
    private RoleMenuMapper roleMenuMapper;
    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private ObjectMapper objectMapper;
    private static final String USER_PERMS_KEY_PREFIX = "user:perms:";
    private static final String USER_MENU_TREE_KEY_PREFIX = "user:menuTree:";
    private static final String MENU_TREE_KEY_PREFIX = "menu:tree:";
    private static final long CACHE_TTL_MINUTES = 30;

    // ==================== tree() — 全量菜单树 ====================

    @Override
    public List<MenuTreeVO> tree() {
        String key = MENU_TREE_KEY_PREFIX + "all";
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                // 缓存命中：JSON → List<Menu> → buildTree
                List<Menu> menus = objectMapper.readValue(json, new TypeReference<List<Menu>>() {});
                return buildTree(menus);
            }
        } catch (Exception e) {
            log.warn("读取菜单树缓存失败，降级查库: {}", e.getMessage());
        }

        // 缓存未命中：查库
        List<Menu> menu = menuMapper.selectList(null);
        try {
            // 写入缓存：List<Menu> → JSON
            stringRedisTemplate.opsForValue().set(
                key, objectMapper.writeValueAsString(menu), CACHE_TTL_MINUTES, TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("写入菜单树缓存失败: {}", e.getMessage());
        }
        return buildTree(menu);
    }

    // ==================== userTree() — 用户动态菜单树 ====================

    public List<MenuTreeVO> userTree(Long userId) {
        String key = USER_MENU_TREE_KEY_PREFIX + userId;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<MenuTreeVO>>() {});
            }
        } catch (Exception e) {
            log.warn("读取用户菜单树缓存失败，降级查库: {}", e.getMessage());
        }
        List<MenuTreeVO> tree = buildTree(getMenusByUserId(userId));
        try {
            stringRedisTemplate.opsForValue().set(
                key, objectMapper.writeValueAsString(tree), CACHE_TTL_MINUTES, TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("写入用户菜单树缓存失败: {}", e.getMessage());
        }
        return tree;
    }

    // ==================== getPermissionsByUserId() — 权限列表 ====================

    public List<String> getPermissionsByUserId(Long userId) {
        String key = USER_PERMS_KEY_PREFIX + userId;
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                return objectMapper.readValue(json, new TypeReference<List<String>>() {});
            }
        } catch (Exception e) {
            log.warn("读取用户权限缓存失败，降级查库: {}", e.getMessage());
        }
        List<String> perms = getMenusByUserId(userId).stream()
                .map(Menu::getPerms)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
        try {
            stringRedisTemplate.opsForValue().set(
                key, objectMapper.writeValueAsString(perms), CACHE_TTL_MINUTES, TimeUnit.MINUTES
            );
        } catch (Exception e) {
            log.warn("写入用户权限缓存失败: {}", e.getMessage());
        }
        return perms;
    }

    // ==================== 缓存清除 ====================

    public void evictUserPermsCache(Long userId) {
        stringRedisTemplate.delete(USER_PERMS_KEY_PREFIX + userId);
        stringRedisTemplate.delete(USER_MENU_TREE_KEY_PREFIX + userId);
    }
}
```

### 4.3 代码解析

#### StringRedisTemplate vs RedisTemplate

| | `RedisTemplate<String, Object>` | `StringRedisTemplate` |
|---|---|---|
| Key 序列化 | 可自定义 | 固定 String |
| Value 序列化 | 可自定义（各种 Serializer） | 固定 String |
| 取出类型 | Object（需强转） | String（无需强转） |
| 优势 | 灵活 | 简单可靠，不会有序列化问题 |
| 劣势 | 序列化方案选错就出 bug | 需要手动 JSON 转换 |

**我们的选择：** `StringRedisTemplate`。因为它的 Value 固定是 String，不存在自动类型推断的问题。JSON 的序列化/反序列化完全由我们用 `ObjectMapper` 手动控制。

#### TypeReference 的作用

```java
// ❌ 这样写会丢失泛型信息（Java 类型擦除）
List<Menu> menus = objectMapper.readValue(json, List.class);
// 实际得到的是 List<LinkedHashMap>，不是 List<Menu>！

// ✅ TypeReference 保留了泛型信息
List<Menu> menus = objectMapper.readValue(json, new TypeReference<List<Menu>>() {});
// 正确还原为 List<Menu>
```

`TypeReference` 是 Jackson 提供的一个抽象类，通过创建匿名子类的方式把泛型信息保留到运行时。这是 Java 泛型类型擦除问题的标准解决方案。

#### 缓存降级（try-catch）

```java
try {
    String json = stringRedisTemplate.opsForValue().get(key);
    if (json != null) {
        return objectMapper.readValue(json, new TypeReference<...>() {});
    }
} catch (Exception e) {
    log.warn("缓存读取失败，降级查库: {}", e.getMessage());
}
// 缓存失败 → 继续查数据库 → 业务不受影响
```

**为什么 catch 整个 Exception？**

缓存只是性能优化，不是业务必需。Redis 挂了、数据损坏、网络异常 → 都应该降级到查库，而不是让整个接口报错。

---

## 五、缓存清除策略

### 5.1 被动过期（TTL）

所有缓存设置了 30 分钟 TTL，到期自动删除。适合菜单这种**修改频率低**的数据。

### 5.2 主动清除

```java
public void evictUserPermsCache(Long userId) {
    stringRedisTemplate.delete(USER_PERMS_KEY_PREFIX + userId);
    stringRedisTemplate.delete(USER_MENU_TREE_KEY_PREFIX + userId);
}
```

当用户的角色或菜单权限被修改时，调用此方法清除该用户的缓存，下次请求会重新查库并写入新缓存。

**注意：** 目前 `tree()` 的全量菜单缓存（`menu:tree:all`）没有主动清除逻辑。如果菜单被修改，需要等 TTL 过期或手动在 RESP 里删除 key。后续可以在菜单管理的增删改接口中加上 `stringRedisTemplate.delete(MENU_TREE_KEY_PREFIX + "all")`。

---

## 六、完整请求流程图

```
客户端请求 GET /menu/tree（带 token）
    │
    ▼
JwtAuthenticationFilter
    ├─ 解析 token → 设置 SecurityContext → 放行
    │
    ▼
MenuController.tree()
    │
    ▼
MenuServiceImpl.tree()
    │
    ├─ StringRedisTemplate.get("menu:tree:all")
    │     │
    │     ├─ 命中 → ObjectMapper.readValue → buildTree → 返回 ✅
    │     │
    │     └─ 未命中 ↓
    │
    ├─ menuMapper.selectList(null)    → 查 MySQL
    │
    ├─ ObjectMapper.writeValueAsString → StringRedisTemplate.set(key, json, 30min)
    │
    └─ buildTree → 返回 ✅
```

---

## 七、Postman 验证步骤

### 7.1 确认缓存写入

1. FLUSHDB 清空 Redis
2. 登录拿 token
3. 带 token 请求 `GET /menu/tree`
4. 在 RESP 中查看：应该能看到 `menu:tree:all` 这个 key，value 是 JSON 数组

### 7.2 确认缓存命中

1. 再次请求 `GET /menu/tree`
2. 控制台**没有 SQL 日志**（说明没查数据库）
3. 响应速度明显变快

### 7.3 确认用户级缓存

1. 请求 `GET /menu/tree/user/1943123456789012001`
2. RESP 中查看 `user:menuTree:1943123456789012001`
3. 请求 `GET /auth/login`（登录会调用 `getPermissionsByUserId`）
4. RESP 中查看 `user:perms:1943123456789012001`

---

## 八、踩坑总结

### 坑 1：`RedisSerializer.json()` 反序列化嵌套对象变 LinkedHashMap

**现象：** 第一次请求正常，后续请求报 ClassCastException
**原因：** GenericJackson2JsonRedisSerializer 存入时带 `@class` 类型信息，但嵌套的内层 List 元素反序列化时变成 LinkedHashMap
**解决：** 放弃使用 `RedisTemplate<String, Object>`

### 坑 2：`activateDefaultTyping(OBJECT_AND_NON_CONCRETE)` 把字符串值当类名

**现象：** `Could not resolve type id 'system:user:list' as a subtype`
**原因：** `OBJECT_AND_NON_CONCRETE` 策略给所有非具体类型（包括 String）加 `@class`，导致 Jackson 把 "system:user:list" 误认为类全限定名
**解决：** 改用 `StringRedisTemplate`，手动控制序列化

### 坑 3：Redis 旧数据格式不一致

**现象：** 改了序列化方案后还是报错
**原因：** Redis 里还存着旧方案写入的数据（格式不同）
**解决：** 每次改序列化方案后，必须 `FLUSHDB` 清空旧数据

### 坑 4：没带 token 以为接口坏了

**现象：** menu/tree 返回空
**原因：** SecurityConfig 的 `anyRequest().authenticated()` 要求认证，不带 token 返回 403
**解决：** 记得在 Postman 的 Authorization header 里加 `Bearer {token}`

---

## 九、下一步待做

1. **Redisson 分布式锁** — 防重复提交（如快速双击新增用户）
2. **缓存更新策略** — 修改菜单时主动清除 `menu:tree:all` 缓存
3. **登录 token Redis 存储（可选）** — 实现主动失效（退出登录/封禁用户后 token 立即失效）

---

## 十、关键概念速查

| 概念 | 一句话解释 |
|---|---|
| `StringRedisTemplate` | Redis 客户端，Key 和 Value 都是 String，简单可靠 |
| `ObjectMapper` | Jackson 的核心类，Java 对象 ↔ JSON 字符串互转 |
| `TypeReference` | 解决 Java 泛型类型擦除，让 Jackson 知道要反序列化成什么类型 |
| `TTL` | 缓存过期时间，到期自动删除 |
| `FLUSHDB` | 清空当前 Redis 数据库的所有数据 |
| 缓存降级 | Redis 出问题时自动回退到查数据库，不影响业务 |
| 缓存穿透 | 查不存在的数据，缓存永远不命中，每次都打到数据库 |
| 缓存击穿 | 热点 key 过期瞬间，大量请求同时打到数据库 |
| 缓存雪崩 | 大量 key 同时过期，Redis 形同虚设 |
