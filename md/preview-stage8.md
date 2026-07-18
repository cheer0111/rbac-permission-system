# 阶段 8 预习：定时任务 + 文件存储

> 阶段 7（RabbitMQ）已完成，本文档为阶段 8 的预习材料，建议在动手前通读一遍。

---

## 目录

1. [阶段概览](#1-阶段概览)
2. [Part A：定时任务](#2-part-a定时任务)
3. [Part B：文件存储](#3-part-b文件存储)
4. [本项目任务拆解](#4-本项目任务拆解)
5. [预习检查清单](#5-预习检查清单)

---

## 1. 阶段概览

| 部分 | 技术 | 解决什么问题 |
|------|------|------------|
| Part A | `@Scheduled` + cron 表达式 | 定时执行清理、报表等周期性任务 |
| Part B | 本地存储 → MinIO 对象存储 | 用户头像/附件上传下载 |

**里程碑**：
- 定时任务：每天凌晨清理过期未处理的操作日志（del_flag 相关）
- 文件存储：用户头像上传到 MinIO，返回可访问的 URL

---

## 2. Part A：定时任务

### 2.1 为什么需要定时任务？

企业系统中到处都是"定时"场景：

| 场景 | 频率 | 举例 |
|------|------|------|
| 数据清理 | 每天 | 删除 30 天前的操作日志 |
| 报表生成 | 每天/每周 | 统计昨日注册用户数、活跃用户数 |
| 超时处理 | 每分钟 | 检查 30 分钟未支付的订单并取消 |
| 缓存刷新 | 定时 | 重新加载字典数据到 Redis |
| 邮件/短信催促 | 定时 | 订单发货后 2 小时提醒用户确认收货 |

### 2.2 Spring Boot 定时任务基础

**开启定时任务支持：**

```java
@SpringBootApplication
@EnableScheduling  // 这一行是关键
public class DemoApplication {
    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }
}
```

> 注意：本项目已经有 `@EnableAsync`，`@EnableScheduling` 是另一个独立注解，需要额外加。

**编写定时任务：**

```java
@Component
@Slf4j
public class ScheduledTasks {

    /**
     * 每天凌晨 2 点执行
     * cron 表达式：秒 分 时 日 月 周
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanExpiredLogs() {
        log.info("开始清理过期操作日志...");
        // 查询 30 天前的日志，执行删除/逻辑删除
        // ...
        log.info("过期日志清理完成");
    }
}
```

### 2.3 cron 表达式详解

cron 由 **6 或 7 个字段**组成（Spring 用 6 位，含秒）：

```
秒  分  时  日  月  周
0   0   2   *   *   ?    → 每天凌晨 2 点
0  30  10   *   *   ?    → 每天上午 10:30
0   0  */6  *   *   ?    → 每 6 小时执行一次
0  */30 *    *   *   ?    → 每 30 分钟执行一次
0   0   2   1   *   ?    → 每月 1 号凌晨 2 点
0   0   2   ?  *  MON    → 每周一凌晨 2 点
```

**各字段允许的值：**

| 字段 | 允许值 | 特殊字符 |
|------|--------|---------|
| 秒 | 0-59 | `,` `-` `*` `/` |
| 分 | 0-59 | `,` `-` `*` `/` |
| 时 | 0-23 | `,` `-` `*` `/` |
| 日 | 1-31 | `,` `-` `*` `/` `?` `L` `W` |
| 月 | 1-12 | `,` `-` `*` `/` |
| 周 | 1-7 (1=SUN...7=SAT) | `,` `-` `*` `/` `?` `L` `#` |

**特殊字符含义：**

| 字符 | 含义 | 示例 |
|------|------|------|
| `*` | 任意值 | `* * * * * ?` = 每秒 |
| `?` | 不指定（日和周互斥，必须有一个是 ?） | `0 0 2 ? * *` |
| `/` | 间隔 | `0 0/30 * * * ?` = 每 30 分钟 |
| `-` | 范围 | `0 0 9-18 * * ?` = 9点到18点每小时 |
| `,` | 列举 | `0 0 8,12,18 * * ?` = 8点、12点、18点 |
| `L` | 最后 | `0 0 2 L * ?` = 每月最后一天凌晨2点 |
| `#` | 第几个星期几 | `0 0 2 ? * 6#3` = 每月第三个星期五 |

**常用 cron 表达式速查：**

```
每隔 5 秒：     0/5 * * * * ?
每隔 1 分钟：   0 * * * * ?
每隔 1 小时：   0 0 * * * ?
每天 23:59：    0 59 23 * * ?
每天凌晨 1 点： 0 0 1 * * ?
每月 1 号凌晨： 0 0 1 1 * ?
周一到周五 9 点：0 0 9 ? * 2-6
```

### 2.4 `@Scheduled` 支持的其他参数

除了 `cron`，还支持以下方式：

```java
// 固定延迟（上次执行完成后，等待 5 秒再执行）
@Scheduled(fixedDelay = 5000)
public void task1() { }

// 固定频率（不管上次有没有执行完，每 5 秒触发一次）
@Scheduled(fixedRate = 5000)
public void task2() { }

// 初始延迟 10 秒后，每 5 秒执行一次
@Scheduled(initialDelay = 10000, fixedRate = 5000)
public void task3() { }
```

**fixedDelay vs fixedRate 对比：**

```
fixedDelay = 5000：
  任务开始 → 执行 3 秒 → 等待 5 秒 → 再次开始 → 执行 3 秒 → 等待 5 秒
  实际间隔：3 + 5 = 8 秒

fixedRate = 5000：
  0秒开始 → 5秒开始 → 10秒开始（不管上次有没有执行完）
  如果执行时间 > 5秒：可能并行执行（需要配合 @Async）
```

### 2.5 企业级定时任务框架：XXL-JOB

**Spring `@Scheduled` 的局限性：**

| 问题 | 说明 |
|------|------|
| 无法动态修改 cron | 修改要改代码重新部署 |
| 无管理界面 | 不知道任务跑了没、成功没成功 |
| 不支持分布式 | 多实例部署时会重复执行 |
| 无失败重试 | 执行失败没有重试机制 |

**XXL-JOB** 是国内最流行的分布式任务调度平台（许雪里开源），解决了以上所有问题：

```
                    ┌──────────────────┐
  管理界面(浏览器) → │   XXL-JOB Admin  │ ← 调度中心（控制 cron、触发任务）
                    └────────┬─────────┘
                             │
                    ┌────────▼─────────┐
  执行器(Java应用) → │   XXL-JOB Exec   │ ← 真正执行任务的代码
                    └──────────────────┘
```

**XXL-JOB vs @Scheduled 对比：**

| 功能 | @Scheduled | XXL-JOB |
|------|-----------|---------|
| cron 动态配置 | ❌ 改代码 | ✅ 管理界面在线改 |
| 可视化管理 | ❌ | ✅ Web 控制台 |
| 分布式协调 | ❌ 多实例重复执行 | ✅ 路由策略（轮询/故障转移） |
| 失败重试 | ❌ | ✅ 自动重试 + 邮件告警 |
| 任务分片 | ❌ | ✅ 广播 + 分片 |

**本阶段先用 @Scheduled，了解企业方案用 XXL-JOB。** XXL-JOB 的部署需要 Docker（阶段 9 会学到），到时候可以升级。

### 2.6 结合本项目：定时任务可以做什么？

结合已有的表和功能，定时任务可以做：

1. **清理过期通知日志**：`sys_notify_log` 表数据量增长快，定期清理 30 天前的记录
2. **刷新菜单缓存**：每天凌晨刷新 Redis 中的菜单树缓存（防止管理员在后台修改了菜单但缓存没更新）
3. **用户状态检查**：每天检查是否有长时间未登录的用户，标记为"不活跃"

---

## 3. Part B：文件存储

### 3.1 为什么需要文件存储？

企业系统常见文件上传场景：

| 场景 | 文件类型 | 大小 |
|------|---------|------|
| 用户头像 | JPG/PNG | 几十 KB ~ 几 MB |
| 附件上传 | PDF/DOC/XLS | 几 MB ~ 几十 MB |
| 商品图片 | JPG/PNG | 几百 KB |
| 视频文件 | MP4 | 几十 MB ~ 几百 MB |
| 日志导出 | CSV/Excel | 几 MB |

### 3.2 方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| 本地存储 | 最简单、零依赖 | 服务器挂了文件丢失、不支持多实例 | 开发/学习/单机小项目 |
| MinIO | S3 兼容、Docker 一键部署 | 需要额外部署 | 中小项目、私有化部署 |
| 阿里云 OSS | 不用自己运维、高可用 | 收费、有供应商锁定 | 生产环境、正式项目 |
| AWS S3 | 全球标准、生态完善 | 国内访问慢、收费 | 海外业务 |

**本项目路线**：先做本地存储（学会原理）→ 再升级 MinIO（学会对象存储）。

### 3.3 本地存储实现

**配置：**

```yaml
# application.yml
file:
  upload:
    path: D:/uploads/   # 上传文件保存的本地目录
    # Linux 改为：/data/uploads/
    max-size: 10MB       # 单文件最大大小
```

**上传接口 Controller：**

```java
@RestController
@RequestMapping("/file")
public class FileController {

    @Value("${file.upload.path}")
    private String uploadPath;

    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        // 1. 校验文件
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }

        // 2. 生成存储路径（按日期分目录，防止一个目录文件太多）
        String datePath = LocalDate.now()
                .format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        File dir = new File(uploadPath + datePath);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        // 3. 生成唯一文件名（防重名）
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        String filename = UUID.randomUUID() + ext;

        // 4. 保存文件
        File dest = new File(dir, filename);
        file.transferTo(dest);

        // 5. 返回可访问的 URL
        String url = "/uploads/" + datePath + "/" + filename;
        return Result.success(url);
    }
}
```

**静态资源映射（让浏览器能访问上传的文件）：**

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Value("${file.upload.path}")
    private String uploadPath;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 把 /uploads/** 的请求映射到本地文件目录
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath + "/");
    }
}
```

**本地存储的缺点：**

- 多实例部署时，A 实例上传的文件 B 实例访问不到
- 服务器磁盘满了怎么办
- 备份、扩容都不方便

### 3.4 MinIO 对象存储

MinIO 是一个 Go 语言编写的高性能对象存储服务器，完全兼容 Amazon S3 API。

**核心概念：**

```
MinIO Server
  └── Bucket（桶）— 类似"文件夹"
       ├── avatar/         — 用户头像
       │    ├── 123.jpg
       │    └── 456.png
       ├── attachment/     — 附件
       └── export/         — 导出文件
```

**Docker 部署（阶段 9 会学）：**

```bash
docker run -p 9000:9000 -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin123 \
  -v D:/minio-data:/data \
  minio/minio server /data --console-address ":9001"
```

- `9000`：API 端口（程序调用）
- `9001`：Web 管理界面（浏览器访问）

**Spring Boot 整合 MinIO：**

```xml
<!-- pom.xml -->
<dependency>
    <groupId>io.minio</groupId>
    <artifactId>minio</artifactId>
    <version>8.5.7</version>
</dependency>
```

```yaml
# application.yml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin123
  bucket: demo-uploads
```

```java
@Configuration
public class MinioConfig {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.access-key}")
    private String accessKey;

    @Value("${minio.secret-key}")
    private String secretKey;

    @Bean
    public MinioClient minioClient() {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
```

```java
@Service
public class MinioService {

    @Autowired
    private MinioClient minioClient;

    @Value("${minio.bucket}")
    private String bucket;

    @Value("${minio.endpoint}")
    private String endpoint;

    /**
     * 上传文件到 MinIO
     */
    public String upload(MultipartFile file, String directory) throws Exception {
        // 确保桶存在
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder()
                .bucket(bucket).build());
        if (!exists) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
        }

        // 生成文件名
        String originalFilename = file.getOriginalFilename();
        String ext = originalFilename.substring(originalFilename.lastIndexOf("."));
        String objectName = directory + "/" + UUID.randomUUID() + ext;

        // 上传
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .stream(file.getInputStream(), file.getSize(), -1)
                .contentType(file.getContentType())
                .build());

        // 返回可访问的 URL
        return endpoint + "/" + bucket + "/" + objectName;
    }

    /**
     * 从 MinIO 删除文件
     */
    public void delete(String objectName) throws Exception {
        minioClient.removeObject(RemoveObjectArgs.builder()
                .bucket(bucket)
                .object(objectName)
                .build());
    }
}
```

### 3.5 结合本项目：文件存储做什么？

1. **用户头像上传**：`POST /user/avatar`，上传后更新 `sys_user.avatar` 字段
2. **操作日志附件**（可选）：操作日志可以关联上传的附件文件

---

## 4. 本项目任务拆解

### Part A：定时任务

| 步骤 | 内容 | 预期产出 |
|------|------|---------|
| 8.1 | DemoApplication 加 `@EnableScheduling` | 定时任务启用 |
| 8.2 | 编写定时任务类：清理过期通知日志 | 每天凌晨清理 30 天前的 notify_log |
| 8.3 | 编写定时任务类：刷新 Redis 菜单缓存 | 每天凌晨刷新菜单树缓存 |

### Part B：文件存储

| 步骤 | 内容 | 预期产出 |
|------|------|---------|
| 8.4 | 本地存储配置 + 上传接口 | `POST /file/upload` 返回文件 URL |
| 8.5 | 静态资源映射 | 浏览器可直接访问上传文件 |
| 8.6 | 用户头像上传接口 | `POST /user/avatar` 上传后更新用户头像 |
| 8.7 | （可选）MinIO 升级 | 替换本地存储为 MinIO |

---

## 5. 预习检查清单

- [ ] 理解 cron 表达式 6 个字段的含义
- [ ] 能写出"每天凌晨 2 点"、"每 30 分钟"、"每月 1 号"的 cron
- [ ] 知道 `@EnableScheduling` 是开启定时任务的注解
- [ ] 知道 `fixedDelay` 和 `fixedRate` 的区别
- [ ] 了解 XXL-JOB 解决了 `@Scheduled` 的哪些痛点
- [ ] 理解本地存储和对象存储的区别
- [ ] 知道 MinIO 的 9000（API）和 9001（管理界面）端口
- [ ] 理解文件上传的基本流程：接收 → 校验 → 生成文件名 → 保存 → 返回 URL
- [ ] 知道为什么上传文件要按日期分目录
- [ ] 知道为什么要用 UUID 生成文件名（防重名）
