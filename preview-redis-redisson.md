# 预习：Stage 5 — Redis 缓存 + Redisson 分布式锁

> 预计内容：1~2 章深度内容，建议先通读本文，理解"为什么要用缓存"和"Redis 是什么"。

---

## 一、本阶段目标

在现有 RBAC 系统中引入 Redis，实现两个功能：

1. **Redis 缓存**：热点数据缓存（如用户信息、菜单树），减少数据库查询压力
2. **Redisson 分布式锁**：防重复提交、防并发修改

---

## 二、为什么要用缓存？

### 2.1 没有缓存时的问题

当前系统每次请求的查询链路：

```
请求 → Controller → Service → Mapper → MySQL
```

假设你的用户管理页面有以下接口调用：

```
GET /menu/tree/user/{id}   → 查 5 张表（user→user_role→role_menu→menu→buildTree）
GET /user/page             → 查 1 张表 + 分页
```

**菜单树查询链路：**
```
userId → sys_user_role（查角色）
  → sys_role_menu（查菜单ID）
    → sys_menu（查菜单详情）
      → Java 内存中 buildTree（递归）
```

一个请求就要查 3 张表，做 1 次递归。如果 100 个用户同时访问呢？100 次 = 300 次数据库查询。

**问题：**
- 数据库压力大（连接池有限，高并发时连接不够用）
- 响应慢（磁盘 IO 比内存 IO 慢 1000 倍以上）
- 菜单数据不会频繁变化，没必要每次都查库

### 2.2 有缓存之后

```
第一次请求：Redis 没有 → 查 MySQL → 结果写入 Redis → 返回给用户
后续请求：Redis 有了 → 直接从 Redis 读取 → 返回给用户（不查 MySQL）
```

**效果：**
- 300 次 MySQL 查询 → 3 次 MySQL 查询 + 297 次 Redis 读取
- Redis 读取速度：微秒级（0.001ms），MySQL 读取：毫秒级（1~10ms）
- 响应速度提升 10~100 倍

---

## 三、Redis 是什么？

### 3.1 基本概念

Redis（Remote Dictionary Server）= **远程字典服务**

- **内存数据库**：数据存在内存中，读写极快
- **键值对存储**：Key-Value 结构，类似 Java 的 HashMap，但是网络可访问的
- **单线程模型**：同一个时刻只处理一个命令，但因为操作都是内存级别，所以极快
- **持久化**：可以把内存数据定期写入磁盘（RDB 快照 / AOF 日志），防止断电丢失

### 3.2 数据类型

Redis 支持多种数据类型，对应不同的使用场景：

| 类型 | Java 类比 | 使用场景 | 项目中的用途 |
|---|---|---|---|
| **String** | `String` | 简单的键值对、计数器 | 存用户 token、验证码 |
| **Hash** | `Map<String, String>` | 对象属性 | 存用户信息（id/name/age...） |
| **List** | `List<String>` | 有序列表、队列 | 消息队列、最新文章列表 |
| **Set** | `Set<String>` | 无序集合、去重 | 存用户的权限标签、共同好友 |
| **ZSet** | `TreeSet / 排行榜` | 有序集合（带分数） | 排行榜、延迟队列 |

### 3.3 常用命令预习

```bash
# String 操作
SET key value           # 存
GET key                 # 取
DEL key                 # 删
SETEX key seconds value # 存并设置过期时间（秒）
TTL key                 # 查看剩余过期时间

# Hash 操作
HSET key field value    # 存一个字段
HGET key field          # 取一个字段
HGETALL key             # 取所有字段
HDEL key field          # 删一个字段

# 通用操作
EXISTS key              # key 是否存在
EXPIRE key seconds      # 设置过期时间
KEYS pattern            # 按模式查找 key（生产环境禁用，用 SCAN 替代）
```

---

## 四、Spring Boot 集成 Redis

### 4.1 依赖

```xml
<!-- Spring Data Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- 连接池（lettuce 自带，但需要 commons-pool2） -->
<dependency>
    <groupId>org.apache.commons</groupId>
    <artifactId>commons-pool2</artifactId>
</dependency>
```

**Lettuce vs Jedis：**
- Spring Boot 2.x 默认用 Lettuce（基于 Netty 的异步客户端，线程安全，连接池高效）
- Jedis 是传统阻塞式客户端，每个线程需要独立连接
- 选 Lettuce 就行，不需要额外引入

### 4.2 配置

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
          max-wait: -1ms     # 获取连接最大等待时间，-1 表示不限
      timeout: 3000ms        # 连接超时时间
```

### 4.3 RedisTemplate vs StringRedisTemplate

Spring Boot 自动配置了两个 Bean：

| Bean | Key 序列化 | Value 序列化 | 用途 |
|---|---|---|---|
| `RedisTemplate` | JdkSerializationRedisSerializer（二进制） | JdkSerializationRedisSerializer | 存 Java 对象 |
| `StringRedisTemplate` | StringRedisSerializer | StringRedisSerializer | 存纯字符串 |

**推荐用法：**

实际项目中，我们通常自定义 `RedisTemplate`，让 Key 用 String、Value 用 JSON：

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory factory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(factory);

        // Key: String 序列化
        template.setKeySerializer(RedisSerializer.string());
        // Value: JSON 序列化（需要 Jackson）
        template.setValueSerializer(RedisSerializer.json());

        // Hash 的 Key 和 Value
        template.setHashKeySerializer(RedisSerializer.string());
        template.setHashValueSerializer(RedisSerializer.json());

        return template;
    }
}
```

**为什么不用默认的 JdkSerialization？**

JdkSerialization 会把对象序列化成二进制，在 Redis 客户端（如 RedisInsight）里看到的是乱码。用 JSON 序列化后，在客户端里可以直接看到可读的 JSON 字符串。

---

## 五、缓存策略

### 5.1 缓存穿透（Cache Penetration）

**场景：** 查一个**数据库里也不存在**的数据。每次都穿透缓存，直接打到数据库。

```
用户查 ID=99999 → Redis 没有 → MySQL 也没有 → 返回 null
下次再来 → Redis 还是没有 → MySQL 还是没有 → 又返回 null
```

**解决方案：**
- 缓存空值：查不到时也缓存一个 `null`（设短过期时间，如 5 分钟）
- 布隆过滤器（Bloom Filter）：提前把所有合法 ID 放入过滤器，查之前先过一遍

### 5.2 缓存击穿（Cache Breakdown）

**场景：** 某个**热点 key 过期**的瞬间，大量请求同时打到数据库。

```
菜单树 key 过期 → 1000 个请求同时发现缓存没了 → 1000 个请求同时查 MySQL
```

**解决方案：**
- 分布式锁：只允许一个请求去查库，其他请求等待，查完后更新缓存
- 随机过期时间：给热点 key 加随机偏移，避免同时过期

### 5.3 缓存雪崩（Cache Avalanche）

**场景：** **大量 key 同时过期**，或者 Redis 宕机，所有请求全部打到数据库。

```
10000 个 key 同时过期 → 10000 个请求同时查 MySQL → 数据库崩溃
```

**解决方案：**
- 过期时间加随机值：TTL = 基础时间 + random(0~300)
- Redis 高可用：主从复制 + 哨兵（Sentinel）

---

## 六、Redisson 分布式锁

### 6.1 为什么需要分布式锁？

**场景 1：防重复提交**

用户快速双击"新增"按钮，浏览器发出两个 POST /user/add 请求：

```
请求A → 查 username 不重复 → INSERT → 成功
请求B → 查 username 不重复 → INSERT → 报错（唯一键冲突）
```

虽然有唯一键兜底，但会产生无意义的报错日志。

**场景 2：防并发修改**

两个管理员同时编辑同一个用户：

```
管理员A 读取用户 → 修改 nickname="张三丰" → 写入
管理员B 读取用户 → 修改 email="zsf@test.com" → 写入（覆盖了A的修改）
```

### 6.2 分布式锁的基本原理

```
加锁：SET key value NX EX seconds
  NX = 如果 key 不存在才设置（互斥）
  EX = 设置过期时间（防死锁）

操作：执行业务逻辑

解锁：DEL key
```

**为什么需要过期时间？**
如果持有锁的进程崩溃了，没有机会执行解锁操作。有过期时间的话，锁会自动释放。

### 6.3 Redisson 是什么？

Redisson 是一个 Redis 的 Java 客户端（比 Jedis/Lettuce 更高级），内置了分布式锁、分布式集合、限流器等工具。

**为什么不用自己实现 SET NX？**

自己实现有两个问题：
- **非原子操作**：SET 和 EXPIRE 是两条命令，如果 SET 成功后进程崩溃，EXPIRE 没执行 → 锁永远不释放 → 死锁
- **不可重入**：同一个线程获取锁后，再次获取会被自己锁住

Redisson 的分布式锁：
- 使用 Lua 脚本保证**原子性**
- 支持**可重入**（同一线程可以多次获取锁）
- 内置**看门狗（Watchdog）**机制，自动续期
- 支持**公平锁、读写锁、信号量**等高级特性

### 6.4 Redisson 基本用法预习

```xml
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.0</version>
</dependency>
```

```java
@Autowired
private RedissonClient redissonClient;

public void someMethod() {
    // 获取锁对象
    RLock lock = redissonClient.getLock("user:add:" + username);

    try {
        // 尝试加锁（等待0秒，锁自动释放时间10秒）
        boolean acquired = lock.tryLock(0, 10, TimeUnit.SECONDS);
        if (!acquired) {
            throw new BusinessException("操作太频繁，请稍后再试");
        }

        // 执行业务逻辑
        // ...

    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
    } finally {
        // 释放锁
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
```

**代码解析：**

| 方法 | 含义 |
|---|---|
| `getLock("key")` | 获取一个名为 "key" 的锁对象（不是加锁，只是创建对象） |
| `tryLock(0, 10, SECONDS)` | 尝试加锁：等待 0 秒（不等待），锁持有时间 10 秒 |
| `isHeldByCurrentThread()` | 检查锁是否是当前线程持有的，防止误释放别人的锁 |
| `unlock()` | 释放锁（放在 finally 里保证一定会执行） |

---

## 七、本阶段在项目中的应用场景

### 7.1 菜单树缓存

```
GET /menu/tree/user/{id}
    → 先查 Redis：key = "menu:tree:user:" + userId
    → 命中 → 直接返回
    → 未命中 → 查数据库 → 写入 Redis（TTL 30分钟）→ 返回
```

用户修改菜单时，清除对应缓存：
```
更新菜单 → DEL "menu:tree:user:*"（按用户清）或 DEL "menu:tree:*"（全清）
```

### 7.2 防重复提交

```
POST /user/add
    → 获取锁：key = "lock:user:add:" + username
    → 获取成功 → 执行新增 → 释放锁
    → 获取失败 → 返回"请勿重复提交"
```

### 7.3 登录 token 存储（可选）

```
登录成功 → token 存入 Redis：key = "login:token:" + token, value = userId
         → 设置过期时间 = JWT 过期时间
每次请求 → 从 Redis 检查 token 是否有效
用户登出 → 从 Redis 删除 token（主动失效）
```

**思考题：**
目前我们的 JWT 是无状态的（签发后无法主动撤销）。如果用户点了"退出登录"或管理员封禁了某个用户，token 在过期前仍然有效。
如果用 Redis 存 token，就可以实现**主动失效**。你觉得有必要加这个功能吗？为什么？

---

## 八、安装 Redis（预习准备）

在开始编码之前，需要在本地安装 Redis：

### Windows（推荐使用 WSL 或 Docker）

```bash
# 方式 1：WSL 中安装
sudo apt update
sudo apt install redis-server
redis-server --daemonize yes

# 方式 2：Docker
docker run -d --name redis -p 6379:6379 redis:7
```

### 验证安装

```bash
redis-cli ping
# 应该返回 PONG
```

### 可视化工具（可选）

- **RedisInsight**（官方，免费）：https://redis.io/insight/
- **Another Redis Desktop Manager**（开源）：https://github.com/qishibo/AnotherRedisDesktopManager

---

## 九、思考题

1. 为什么缓存适合存菜单树，不适合存用户分页列表？
2. 如果 Redis 宕机了，你的系统应该怎么处理？（缓存挂了 → 服务还能用吗？）
3. `tryLock(0, 10, SECONDS)` 中的三个参数分别代表什么？如果把第一个参数改成 3，行为有什么变化？
4. 为什么 `unlock()` 要放在 `finally` 块里？如果不放，可能出什么问题？

---

## 十、下一天任务预览

任务将从以下步骤展开：

1. **安装 Redis** → 本地启动并验证连通
2. **Spring Boot 集成 Redis** → pom.xml 加依赖 + yml 配置 + RedisTemplate 配置
3. **实现菜单树缓存** → 先查缓存，没有再查库，结果写入缓存
4. **Spring Boot 集成 Redisson** → 分布式锁实现防重复提交
5. **缓存更新策略** → 修改数据时清除缓存

预计工作量：菜单树缓存（重点）+ Redisson 防重复提交（核心），其余为可选扩展。
