# RabbitMQ 完全指南（结合本项目）

> 本文档基于本项目 cheer.demo（Spring Boot 3.5 + RabbitMQ 3.13）的实际代码编写，从基础到进阶，涵盖企业级使用场景。

---

## 目录

1. [基础概念回顾](#1-基础概念回顾)
2. [本项目代码实现详解](#2-本项目代码实现详解)
3. [交换机类型详解](#3-交换机类型详解)
4. [消息可靠性机制](#4-消息可靠性机制)
5. [死信队列与延迟队列](#5-死信队列与延迟队列)
6. [消息幂等性](#6-消息幂等性)
7. [消费者限流与预取](#7-消费者限流与预取)
8. [企业级使用场景](#8-企业级使用场景)
9. [常见问题与排错](#9-常见问题与排错)
10. [学习路线建议](#10-学习路线建议)

---

## 1. 基础概念回顾

### 1.1 RabbitMQ 是什么？

RabbitMQ 是基于 Erlang 语言开发的 **AMQP（Advanced Message Queuing Protocol）** 消息中间件。它的核心作用是**解耦、异步、削峰**。

**类比理解：**

```
没有 MQ：  用户注册 → 注册入库 → 发邮件 → 发短信 → 发通知（全部同步，用户等半天）
有 MQ：    用户注册 → 注册入库 → 投递消息到 MQ → 立即返回"注册成功"
          MQ → 消费者A：发邮件
          MQ → 消费者B：发短信
          MQ → 消费者C：发通知
```

### 1.2 核心模型

```
Producer（生产者）→ Exchange（交换机）→ Binding（绑定）→ Queue（队列）→ Consumer（消费者）
```

| 组件 | 作用 | 类比 |
|------|------|------|
| **Producer** | 发送消息的应用 | 寄件人 |
| **Exchange** | 接收消息，按规则路由到 Queue | 邮局分拣中心 |
| **Binding** | Exchange 到 Queue 的路由规则 | 分拣规则（按地址、按邮编） |
| **Queue** | 存储消息的缓冲区 | 邮箱 |
| **Consumer** | 从 Queue 取消息并处理 | 收件人 |

### 1.3 连接相关端口

| 端口 | 协议 | 用途 |
|------|------|------|
| 5672 | AMQP | 程序连接（生产者/消费者） |
| 15672 | HTTP | Web 管理界面 |
| 25672 | Erlang 分布式 | 节点间通信 |

---

## 2. 本项目代码实现详解

### 2.1 依赖引入

```xml
<!-- pom.xml -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

这个 starter 自动引入了：
- `spring-amqp`：Spring AMQP 核心抽象
- `spring-rabbit`：RabbitMQ 的具体实现
- `amqp-client`：RabbitMQ 官方 Java 客户端

### 2.2 配置文件

```yaml
# application.yml
spring:
  rabbitmq:
    host: localhost
    port: 5672          # AMQP 协议端口（不是 15672）
    username: guest
    password: guest
    virtual-host: /     # 虚拟主机，类似 MySQL 的"数据库"
    # 发布确认：消息到达 Exchange 时回调
    publisher-confirm-type: correlated
    # 发布返回：消息从 Exchange 无法路由到 Queue 时回调
    publisher-returns: true
```

**各配置项详解：**

- `virtual-host`：RabbitMQ 支持多租户隔离，不同 vhost 之间的 Exchange/Queue/消息完全隔离。默认 `/`。生产环境中通常按业务划分，如 `/order`、`/user`、`/notification`。

- `publisher-confirm-type`：
  - `NONE`：不确认（默认）
  - `CORRELATED`：异步回调，带 correlationId，推荐
  - `SIMPLE`：同步等待确认（阻塞，性能差，不推荐）

- `publisher-returns`：当消息无法路由到任何 Queue 时，RabbitMQ 会将消息返回给生产者。

### 2.3 序列化配置

```java
@Configuration
public class RabbitMQConfig {

    /**
     * JSON 序列化转换器
     *
     * 为什么需要：RabbitTemplate 默认用 SimpleMessageConverter（Java 原生序列化），
     * 发出的消息是二进制格式，可读性差，跨语言也不兼容。
     * 换成 Jackson 后，消息以 JSON 格式存储，可读、可调试、跨语言友好。
     */
    @Bean
    public Jackson2JsonMessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}
```

**SimpleMessageConverter vs Jackson2JsonMessageConverter：**

```
SimpleMessageConverter 发出的消息（二进制不可读）：
  Content-Type: application/x-java-serialized-object
  Body: ￎ￭ﾥ￪ﾳ...

Jackson2JsonMessageConverter 发出的消息（JSON 可读）：
  Content-Type: application/json
  Body: {"userId": 1234567890, "username": "zhangsan"}
```

### 2.4 交换机 + 队列 + 绑定声明

```java
@Configuration
public class RabbitMQConfig {

    // 常量集中管理，避免硬编码
    public static final String WELCOME_EXCHANGE = "exchange.welcome";
    public static final String WELCOME_QUEUE = "queue.welcome";
    public static final String WELCOME_ROUTING_KEY = "welcome";

    /**
     * 直连交换机
     * 路由规则：routingKey 完全匹配才转发
     */
    @Bean
    public DirectExchange welcomeExchange() {
        // 参数：name, durable(持久化), autoDelete(自动删除)
        return new DirectExchange(WELCOME_EXCHANGE, true, false);
    }

    /**
     * 队列
     * durable=true：RabbitMQ 重启后队列不丢失
     */
    @Bean
    public Queue welcomeQueue() {
        // 参数：name, durable
        return new Queue(WELCOME_QUEUE, true);
    }

    /**
     * 绑定：将 Queue 绑到 Exchange，指定 routingKey
     */
    @Bean
    public Binding welcomeBinding(Queue welcomeQueue, DirectExchange welcomeExchange) {
        return BindingBuilder.bind(welcomeQueue)
                .to(welcomeExchange)
                .with(WELCOME_ROUTING_KEY);
    }
}
```

**为什么用 @Bean 声明而不是在代码中手动创建？**

Spring Boot 启动时会自动检查这些 Bean，如果 RabbitMQ 中不存在对应的 Exchange/Queue/Binding，会自动创建。这比手动去管理台创建更可靠——**代码即配置，不会漏掉任何基础设施**。

### 2.5 生产者发送消息

```java
@Service
public class UserServiceImpl implements UserService {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Override
    public User add(UserDTO userDTO) {
        // ... 注册入库逻辑 ...

        // 构建消息体（Map 会被 Jackson 自动转 JSON）
        Map<String, Object> message = new HashMap<>();
        message.put("userId", user.getId());
        message.put("username", user.getUsername());

        // 发送消息到 MQ
        rabbitTemplate.convertAndSend(
            RabbitMQConfig.WELCOME_EXCHANGE,     // 交换机
            RabbitMQConfig.WELCOME_ROUTING_KEY,   // 路由键
            message                                // 消息体
        );

        return user;
    }
}
```

**`RabbitTemplate.convertAndSend` 三个参数的含义：**

1. **Exchange 名**：消息发到哪个交换机
2. **Routing Key**：交换机根据这个 key 决定路由到哪个队列
3. **消息体**：实际要传的数据（Map/List/String/自定义对象均可）

**执行流程：**
```
convertAndSend("exchange.welcome", "welcome", {userId: 1, username: "zhangsan"})
    → Exchange "exchange.welcome" 收到消息
    → 查找绑定规则：routingKey="welcome" → 匹配到 queue.welcome
    → 消息进入 queue.welcome
    → Consumer 从 queue.welcome 取出消息并处理
```

### 2.6 消费者接收消息

```java
@Slf4j
@Component
public class MqConsumer {

    @Autowired
    private NotifyLogMapper notifyLogMapper;

    /**
     * 监听 queue.welcome，有消息自动消费
     * 参数类型 Map<String, Object>：Jackson 自动将 JSON 反序列化为 Map
     */
    @RabbitListener(queues = RabbitMQConfig.WELCOME_QUEUE)
    public void handleWelcome(Map<String, Object> message) {
        try {
            Long userId = ((Number) message.get("userId")).longValue();
            String username = (String) message.get("username");

            // 构建通知日志并入库
            NotifyLog notifyLog = new NotifyLog();
            notifyLog.setUserId(userId);
            notifyLog.setUsername(username);
            notifyLog.setNotifyType("welcome");
            notifyLog.setTitle("欢迎注册");
            notifyLog.setContent("欢迎 " + username + " 加入系统");
            notifyLog.setStatus(0);
            notifyLog.setCreateTime(LocalDateTime.now());

            notifyLogMapper.insert(notifyLog);
            log.info("欢迎通知发送成功：userId={}, username={}", userId, username);
        } catch (Exception e) {
            log.error("欢迎通知发送失败", e);
            // 记录失败日志（即使失败也要记录，方便排查）
            try {
                NotifyLog failLog = new NotifyLog();
                failLog.setNotifyType("welcome");
                failLog.setTitle("欢迎注册");
                failLog.setStatus(1);
                failLog.setErrorMsg(e.getMessage());
                failLog.setCreateTime(LocalDateTime.now());
                notifyLogMapper.insert(failLog);
            } catch (Exception ex) {
                log.error("通知日志写入也失败", ex);
            }
        }
    }
}
```

**关键点解释：**

- `@RabbitListener(queues = "xxx")`：Spring AMQP 自动创建容器，持续监听队列，有消息就调用此方法
- `((Number) message.get("userId")).longValue()`：Jackson 反序列化 JSON 数字时，默认是 Integer。用 `Number.longValue()` 安全地转为 Long，避免类型转换异常
- **双层 try-catch**：内层 catch 处理消费失败（记日志），外层保证即使记日志也失败也不会抛到 MQ 框架导致无限重试

---

## 3. 交换机类型详解

### 3.1 Direct Exchange（直连交换机）

**路由规则**：routingKey 必须完全匹配。

```
Producer → "order.create" → DirectExchange → Binding(routingKey="order.create") → Queue A
Producer → "order.create" → DirectExchange → Binding(routingKey="order.cancel") → 不匹配，丢弃
```

**本项目用法**：欢迎通知，固定 routingKey="welcome"，一对一精确匹配。

```java
// 声明
@Bean
public DirectExchange orderExchange() {
    return new DirectExchange("exchange.order");
}

// 可以绑定多个队列到同一个 Exchange，用不同 routingKey 区分
@Bean
public Binding orderCreateBinding() {
    return BindingBuilder.bind(orderCreateQueue())
            .to(orderExchange())
            .with("order.create");
}

@Bean
public Binding orderCancelBinding() {
    return BindingBuilder.bind(orderCancelQueue())
            .to(orderExchange())
            .with("order.cancel");
}
```

**适用场景**：任务分发，不同类型任务路由到不同队列（如"邮件"队列、"短信"队列、"推送"队列）。

### 3.2 Topic Exchange（主题交换机）

**路由规则**：routingKey 支持通配符。

- `*`：匹配一个单词
- `#`：匹配零个或多个单词

```
Producer → "order.create.success" → TopicExchange → Binding(order.create.*) → Queue A ✓
Producer → "order.create.success" → TopicExchange → Binding(order.#)         → Queue B ✓
Producer → "order.create.success" → TopicExchange → Binding(order.cancel.*)    → 不匹配
Producer → "user.register"         → TopicExchange → Binding(#.register)        → Queue C ✓
```

**代码示例（结合本项目扩展）：**

```java
@Configuration
public class TopicMQConfig {

    public static final String NOTIFY_EXCHANGE = "exchange.notify";

    // 通知队列：接收所有 notify.* 的消息
    public static final String NOTIFY_QUEUE = "queue.notify";
    public static final String NOTIFY_LOG_QUEUE = "queue.notify.log";

    @Bean
    public TopicExchange notifyExchange() {
        return new TopicExchange(NOTIFY_EXCHANGE);
    }

    @Bean
    public Queue notifyQueue() {
        return new Queue(NOTIFY_QUEUE, true);
    }

    @Bean
    public Queue notifyLogQueue() {
        return new Queue(NOTIFY_LOG_QUEUE, true);
    }

    // 所有 notify.* 消息都发到通知队列
    @Bean
    public Binding notifyBinding() {
        return BindingBuilder.bind(notifyQueue())
                .to(notifyExchange())
                .with("notify.*");
    }

    // 只有 notify.log.* 的消息发到日志队列
    @Bean
    public Binding notifyLogBinding() {
        return BindingBuilder.bind(notifyLogQueue())
                .to(notifyExchange())
                .with("notify.log.*");
    }
}
```

```java
// 发送
rabbitTemplate.convertAndSend("exchange.notify", "notify.email", message);   // → notifyQueue
rabbitTemplate.convertAndSend("exchange.notify", "notify.log.login", message); // → notifyQueue + notifyLogQueue
rabbitTemplate.convertAndSend("exchange.notify", "notify.sms", message);     // → notifyQueue
```

**适用场景**：通知系统（邮件/短信/站内信）、日志分级（info/warn/error 不同队列）。

### 3.3 Fanout Exchange（扇出交换机）

**路由规则**：忽略 routingKey，消息广播到所有绑定的 Queue。

```
Producer → FanoutExchange → Queue A（收到）
                       → Queue B（收到）
                       → Queue C（收到）
```

```java
@Configuration
public class FanoutMQConfig {

    public static final String BROADCAST_EXCHANGE = "exchange.broadcast";

    @Bean
    public FanoutExchange broadcastExchange() {
        return new FanoutExchange(BROADCAST_EXCHANGE);
    }

    @Bean
    public Queue emailQueue() {
        return new Queue("queue.broadcast.email", true);
    }

    @Bean
    public Queue smsQueue() {
        return new Queue("queue.broadcast.sms", true);
    }

    @Bean
    public Queue pushQueue() {
        return new Queue("queue.broadcast.push", true);
    }

    // Fanout 绑定不需要 routingKey
    @Bean
    public Binding emailBinding() {
        return BindingBuilder.bind(emailQueue()).to(broadcastExchange());
    }

    @Bean
    public Binding smsBinding() {
        return BindingBuilder.bind(smsQueue()).to(broadcastExchange());
    }

    @Bean
    public Binding pushBinding() {
        return BindingBuilder.bind(pushQueue()).to(broadcastExchange());
    }
}
```

```java
// 发送（routingKey 传空字符串即可）
rabbitTemplate.convertAndSend("exchange.broadcast", "", message);
// 所有绑定的队列都会收到消息
```

**适用场景**：广播通知（系统公告发给所有渠道）、缓存失效（更新数据后通知所有服务清缓存）。

### 3.4 四种交换机对比

| 类型 | 路由规则 | 适用场景 | 本项目是否使用 |
|------|---------|---------|---------------|
| Direct | routingKey 精确匹配 | 任务分发 | ✅ 已使用 |
| Topic | routingKey 通配符匹配 | 通知分级、日志分级 | 可扩展 |
| Fanout | 广播到所有绑定队列 | 广播、缓存同步 | 可扩展 |
| Headers | 消息头属性匹配（少用） | 复杂条件路由 | 不推荐 |

---

## 4. 消息可靠性机制

消息从生产者到消费者，链路上有**三个阶段可能丢消息**：

```
生产者 → Exchange → Queue → 消费者
  ①         ②              ③
  阶段一    阶段二         阶段三
```

### 4.1 阶段一：生产者 → Exchange（发布确认）

**问题**：消息发出去了，但没到达 Exchange（网络抖动、Exchange 不存在）。

**解决**：`publisher-confirm-type: correlated` + 回调函数。

```java
@Component
public class RabbitConfirmCallback implements RabbitTemplate.ConfirmCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        rabbitTemplate.setConfirmCallback(this);
    }

    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (ack) {
            // 消息成功到达 Exchange
            log.info("消息发送成功，correlationId={}", correlationData != null ? correlationData.getId() : "null");
        } else {
            // 消息未到达 Exchange（Exchange 不存在、网络问题等）
            log.error("消息发送失败，correlationId={}，原因：{}", correlationData != null ? correlationData.getId() : "null", cause);
            // 这里可以：1.重试  2.记日志  3.告警
        }
    }
}
```

**带 CorrelationData 的发送（方便追踪消息）：**

```java
CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
rabbitTemplate.convertAndSend(exchange, routingKey, message, correlationData);
```

### 4.2 阶段二：Exchange → Queue（发布返回）

**问题**：消息到了 Exchange，但找不到匹配的 Queue（routingKey 错误、没有绑定）。

**解决**：`publisher-returns: true` + 回调函数。

```java
@Component
public class RabbitReturnCallback implements RabbitTemplate.ReturnsCallback {

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @PostConstruct
    public void init() {
        rabbitTemplate.setReturnsCallback(this);
    }

    @Override
    public void returnedMessage(ReturnedMessage returned) {
        log.error("消息无法路由到队列！Exchange={}，RoutingKey={}，ReplyCode={}，ReplyText={}",
                returned.getExchange(),
                returned.getRoutingKey(),
                returned.getReplyCode(),
                returned.getReplyText());
        // 这里可以：1.修正 routingKey 重试  2.存入死信  3.人工介入
    }
}
```

### 4.3 阶段三：消费者处理（ACK 确认机制）

**问题**：消费者收到消息后处理到一半挂了，消息丢了。

**默认行为**：Spring AMQP 的 `AUTO` 模式——方法正常返回就 ACK，抛异常就 NACK 并重试。

**手动 ACK 模式（更可控）：**

```yaml
# application.yml
spring:
  rabbitmq:
    listener:
      simple:
        acknowledge-mode: manual  # 手动确认
```

```java
@RabbitListener(queues = RabbitMQConfig.WELCOME_QUEUE)
public void handleWelcome(Map<String, Object> message,
                         Channel channel,    // RabbitMQ 原生 Channel
                         @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
    try {
        // 处理消息...
        Long userId = ((Number) message.get("userId")).longValue();
        String username = (String) message.get("username");
        // ... 业务逻辑 ...

        // 处理成功，手动 ACK
        channel.basicAck(deliveryTag, false);
    } catch (Exception e) {
        log.error("消息消费失败", e);
        try {
            // 处理失败，拒绝消息
            // third param: requeue=true 重新入队（会再次消费）
            //              requeue=false 丢弃或进死信队列（推荐）
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException ex) {
            log.error("NACK 操作失败", ex);
        }
    }
}
```

### 4.4 消息持久化

确保三个环节都持久化：

| 环节 | 配置方式 | 说明 |
|------|---------|------|
| Exchange 持久化 | `new DirectExchange(name, true, false)` 第二个参数 `durable=true` | **本项目已配置** |
| Queue 持久化 | `new Queue(name, true)` 第二个参数 `durable=true` | **本项目已配置** |
| 消息持久化 | `MessagePostProcessor` 设置 `MessageProperties#setDeliveryMode` | 默认就是持久化 |

**消息持久化代码：**

```java
rabbitTemplate.convertAndSend(exchange, routingKey, message, msg -> {
    msg.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    return msg;
});
```

> 注意：Spring AMQP 配合 Jackson2JsonMessageConverter 时，消息默认就是 PERSISTENT 模式，通常不需要手动设置。

---

## 5. 死信队列与延迟队列

### 5.1 什么是死信？

一条消息变成"死信"的三种情况：

1. 消息被消费者 **NACK 且 requeue=false**
2. 队列达到 **最大长度**，新消息被丢弃
3. 消息 **TTL 过期** 未被消费

死信会被投递到绑定的 **死信交换机（DLX）**。

### 5.2 死信队列配置

```java
@Configuration
public class DeadLetterConfig {

    // ==================== 死信交换机和队列 ====================
    public static final String DLX_EXCHANGE = "exchange.dlx";
    public static final String DLX_QUEUE = "queue.dlx";

    @Bean
    public DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue dlxQueue() {
        return new Queue(DLX_QUEUE, true);
    }

    @Bean
    public Binding dlxBinding() {
        return BindingBuilder.bind(dlxQueue())
                .to(dlxExchange())
                .with("dlx");
    }

    // ==================== 普通队列（绑定死信） ====================
    @Bean
    public Queue normalQueue() {
        Map<String, Object> args = new HashMap<>();
        // 绑定死信交换机
        args.put("x-dead-letter-exchange", DLX_EXCHANGE);
        // 死信路由键
        args.put("x-dead-letter-routing-key", "dlx");
        // 队列最大长度（可选，超出变成死信）
        // args.put("x-max-length", 1000);
        return new Queue("queue.normal", true, false, false, args);
    }
}
```

### 5.3 延迟队列（TTL + 死信实现）

**经典场景**：电商订单 30 分钟未支付自动取消。

```
Producer → 正常队列（TTL=30min）→ 30分钟后过期 → 死信交换机 → 死信队列 → 消费者取消订单
```

```java
@Configuration
public class DelayQueueConfig {

    public static final String DELAY_EXCHANGE = "exchange.delay";
    public static final String DELAY_QUEUE = "queue.delay";
    public static final String DELAY_DLX_EXCHANGE = "exchange.delay.dlx";
    public static final String DELAY_DLX_QUEUE = "queue.delay.dlx";

    // ==================== 延迟队列（带 TTL） ====================
    @Bean
    public DirectExchange delayExchange() {
        return new DirectExchange(DELAY_EXCHANGE);
    }

    @Bean
    public Queue delayQueue() {
        Map<String, Object> args = new HashMap<>();
        // 消息 TTL：30 分钟（毫秒）
        args.put("x-message-ttl", 30 * 60 * 1000);
        // 过期后投递到死信交换机
        args.put("x-dead-letter-exchange", DELAY_DLX_EXCHANGE);
        args.put("x-dead-letter-routing-key", "delay.dlx");
        return new Queue(DELAY_QUEUE, true, false, false, args);
    }

    @Bean
    public Binding delayBinding() {
        return BindingBuilder.bind(delayQueue())
                .to(delayExchange())
                .with("delay.order");
    }

    // ==================== 死信队列（真正消费的地方） ====================
    @Bean
    public DirectExchange delayDlxExchange() {
        return new DirectExchange(DELAY_DLX_EXCHANGE);
    }

    @Bean
    public Queue delayDlxQueue() {
        return new Queue(DELAY_DLX_QUEUE, true);
    }

    @Bean
    public Binding delayDlxBinding() {
        return BindingBuilder.bind(delayDlxQueue())
                .to(delayDlxExchange())
                .with("delay.dlx");
    }
}
```

```java
// 生产者：创建订单时发送延迟消息
public void createOrder(Order order) {
    orderMapper.insert(order);

    Map<String, Object> message = new HashMap<>();
    message.put("orderId", order.getId());
    message.put("userId", order.getUserId());

    // 发送到延迟队列，30 分钟后过期才会被消费
    rabbitTemplate.convertAndSend(
        DelayQueueConfig.DELAY_EXCHANGE,
        "delay.order",
        message
    );
}

// 消费者：收到过期消息（说明 30 分钟未支付）
@RabbitListener(queues = DelayQueueConfig.DELAY_DLX_QUEUE)
public void handleOrderTimeout(Map<String, Object> message) {
    Long orderId = ((Number) message.get("orderId")).longValue();
    // 检查订单是否已支付，未支付则取消
    Order order = orderMapper.selectById(orderId);
    if (order != null && order.getStatus() == 0) {
        order.setStatus(-1); // 已取消
        orderMapper.updateById(order);
        log.info("订单超时自动取消：orderId={}", orderId);
    }
}
```

> **进阶**：RabbitMQ 3.12+ 推荐用 **Delayed Message Plugin**（`rabbitmq_delayed_message_exchange`），比 TTL+死信方案更直观。但 TTL+死信方案兼容性更好，面试也常考。

---

## 6. 消息幂等性

### 6.1 为什么需要幂等？

网络抖动可能导致消息被重复投递。消费者可能收到同一条消息两次：

```
第一次：消息 → 消费者处理成功 → ACK
网络抖动导致 ACK 丢失
MQ 以为消费者没处理 → 重新投递
第二次：同一条消息 → 消费者再次处理 → 如果业务不幂等，就会出问题
```

### 6.2 常见解决方案

**方案一：数据库唯一约束（适合本项目）**

```java
@RabbitListener(queues = RabbitMQConfig.WELCOME_QUEUE)
public void handleWelcome(Map<String, Object> message) {
    Long userId = ((Number) message.get("userId")).longValue();
    String username = (String) message.get("username");

    // 利用唯一索引保证幂等：sys_notify_log 加唯一索引 (user_id, notify_type)
    // 第二次 insert 会因为唯一约束冲突而失败，catch 住即可
    try {
        NotifyLog notifyLog = new NotifyLog();
        notifyLog.setUserId(userId);
        notifyLog.setUsername(username);
        notifyLog.setNotifyType("welcome");
        notifyLog.setTitle("欢迎注册");
        notifyLog.setContent("欢迎 " + username + " 加入系统");
        notifyLog.setStatus(0);
        notifyLog.setCreateTime(LocalDateTime.now());
        notifyLogMapper.insert(notifyLog);
    } catch (DuplicateKeyException e) {
        log.info("重复消息，跳过：userId={}", userId);
        // 重复消息，说明之前已经处理过了，直接跳过
    }
}
```

```sql
-- 给 sys_notify_log 加唯一索引
ALTER TABLE sys_notify_log ADD UNIQUE INDEX uk_user_notify (user_id, notify_type);
```

**方案二：Redis 去重（适合高并发场景）**

```java
@RabbitListener(queues = RabbitMQConfig.WELCOME_QUEUE)
public void handleWelcome(Map<String, Object> message) {
    String msgId = (String) message.get("msgId");

    // setIfAbsent：只在 key 不存在时设置，返回 true 表示首次
    Boolean firstTime = redisTemplate.opsForValue()
            .setIfAbsent("mq:consumed:" + msgId, "1", 24, TimeUnit.HOURS);

    if (Boolean.FALSE.equals(firstTime)) {
        log.info("重复消息，跳过：msgId={}", msgId);
        return;
    }

    // 首次消费，正常处理...
}
```

**方案三：全局唯一消息 ID + 数据库记录表**

```java
// 专门的消费记录表
CREATE TABLE mq_consume_log (
    msg_id VARCHAR(64) PRIMARY KEY,
    queue_name VARCHAR(100),
    consume_time DATETIME
);

// 消费前先检查
if (consumeLogMapper.selectById(msgId) != null) {
    return; // 已消费过
}
consumeLogMapper.insert(new ConsumeLog(msgId, queueName));
// 正常处理...
```

### 6.3 三种方案对比

| 方案 | 优点 | 缺点 | 适用场景 |
|------|------|------|---------|
| 数据库唯一约束 | 简单可靠 | 需要能从业务数据构造唯一键 | 本项目（userId+notifyType 天然唯一） |
| Redis 去重 | 性能好 | 依赖 Redis，Key 过期前有效 | 高并发、短时间窗口 |
| 消费记录表 | 通用，不依赖业务 | 多一张表 | 通用方案 |

---

## 7. 消费者限流与预取

### 7.1 为什么需要限流？

如果消息量突然暴增，消费者全部取出来处理，可能 OOM。需要控制消费者**每次只取几条消息**。

### 7.2 配置预取数量

```yaml
# application.yml
spring:
  rabbitmq:
    listener:
      simple:
        prefetch: 10  # 每个消费者每次最多取 10 条消息
        # 默认是 250，太大了，建议设为 10~50
```

**原理：**
```
prefetch=1：  一次取1条，处理完再取下一条（最安全，但吞吐量低）
prefetch=10： 一次取10条，并发处理10条（推荐，安全+性能平衡）
prefetch=250：一次取250条（默认值，容易 OOM）
```

---

## 8. 企业级使用场景

### 8.1 异步通知（本项目已实现）

```
用户注册 → MQ → 发欢迎邮件
                 → 发欢迎短信
                 → 写通知日志
```

**企业场景**：注册、登录、订单状态变更等场景的异步通知。某电商系统每天 1000 万订单，每个订单状态变更要发 3-5 条通知，同步调用会拖垮主流程。

### 8.2 订单超时取消（延迟队列）

```
用户下单 → MQ（TTL=30min）→ 30分钟后检查支付状态 → 未支付则取消 → 释放库存
```

**企业场景**：所有电商（淘宝、京东、拼多多）都有这个机制。每天几十万未支付订单，不可能用定时任务轮询。

### 8.3 削峰填谷

```
秒杀场景：
  平时：100 QPS → MQ → 消费者慢慢处理
  秒杀：100000 QPS → MQ → 消费者按自身能力消费（不会被打爆）
```

```java
// 生产者（秒杀接口，极快）
@PostMapping("/seckill")
public Result seckill(@RequestParam Long goodsId) {
    // 不直接处理订单，只接收请求
    Map<String, Object> message = Map.of("userId", getCurrentUserId(), "goodsId", goodsId);
    rabbitTemplate.convertAndSend("exchange.seckill", "seckill.order", message);
    return Result.success("排队中");
}

// 消费者（按能力消费，不会 OOM）
@RabbitListener(queues = "queue.seckill.order")
public void handleSeckill(Map<String, Object> message) {
    // 逐个处理：扣库存、创建订单...
}
```

**企业场景**：双 11 秒杀、火车票抢票、红包抢夺。核心思想是"把瞬时高并发转为可控的消费流"。

### 8.4 系统解耦

```
用户服务 → MQ → 积分服务（加积分）
               → 优惠券服务（发券）
               → 搜索服务（更新索引）
               → 推荐服务（更新画像）
```

**企业场景**：微服务架构中，服务间用 MQ 解耦。新增一个下游服务只需要新增一个消费者，不需要改上游代码。

### 8.5 日志异步落库（本项目可扩展）

```
AOP 切面拦截 → MQ → 消费者异步写 operate_log 表

替代当前的 @Async 方案：
  @Async：应用内线程池，应用挂了任务丢失
  MQ：消息持久化，应用挂了重启后继续消费
```

**企业场景**：高并发系统（每秒上万次操作）的操作日志，同步写库会拖慢主业务，用 MQ 异步落库是标配。

### 8.6 分布式事务的最终一致性

```
场景：下单 + 扣库存（两个不同服务/数据库）

方案一（2PC/XA）：强一致性，但性能差，企业少用
方案二（MQ 最终一致性）：
  1. 下单服务：创建订单 + 发消息到 MQ（同一事务）
  2. 库存服务：消费消息 → 扣库存
  3. 如果扣库存失败：MQ 重试 / 人工补偿
```

```java
// 本地消息表方案（保证消息一定能发出去）
@Transactional
public void createOrder(Order order) {
    orderMapper.insert(order);

    // 同时写入本地消息表（和订单在同一个事务中）
    LocalMessage message = new LocalMessage();
    message.setBizId(order.getId());
    message.setExchange("exchange.order");
    message.setRoutingKey("order.create");
    message.setContent(toJson(order));
    localMessageMapper.insert(message);
    // 事务提交后，定时任务扫描本地消息表，发送到 MQ
}
```

---

## 9. 常见问题与排错

### 9.1 Connection refused

```
java.net.ConnectException: Connection refused: getsockopt
```

**原因**：RabbitMQ 没启动。

**解决**：`rabbitmq-server` 启动服务。

### 9.2 Access denied

```
ACCESS_DENIED - Login was refused using authentication mechanism PLAIN
```

**原因**：用户名密码错误。

**解决**：检查 `application.yml` 中的 `spring.rabbitmq.username/password`。

### 9.3 端口被占用（BOOT FAILED）

```
ERROR: could not bind to distribution port 25672, it is in use
```

**原因**：旧版 RabbitMQ 进程/服务还在运行。

**解决**：`taskkill /F /IM erl.exe` + `sc delete RabbitMQ`（本项目踩过的坑）。

### 9.4 消费者收到的消息类型不对

```
ClassCastException: Cannot cast java.lang.Integer to java.lang.Long
```

**原因**：Jackson 反序列化 JSON 数字时默认用 Integer。

**解决**：用 `((Number) message.get("key")).longValue()` 安全转换。

### 9.5 消息堆积

**现象**：RabbitMQ 管理界面中某队列消息数持续增长。

**排查步骤**：
1. 检查消费者是否正常启动
2. 检查消费者是否有异常（日志）
3. 增加 prefetch 值或增加消费者实例
4. 检查是否有慢查询拖慢消费速度

---

## 10. 学习路线建议

```
当前阶段（已掌握）：
  ✅ 基础概念（Exchange/Queue/Binding）
  ✅ Direct Exchange
  ✅ RabbitTemplate 发消息
  ✅ @RabbitListener 消费消息
  ✅ Jackson JSON 序列化

进阶一（建议接下来练）：
  □ Topic Exchange / Fanout Exchange
  □ 消费者手动 ACK
  □ 发布确认回调（ConfirmCallback + ReturnCallback）
  □ 消息幂等性

进阶二（企业面试重点）：
  □ 死信队列 + 延迟队列（TTL 方案）
  □ 消息可靠性三阶段保障
  □ 本地消息表 + 最终一致性
  □ 消费者限流（prefetch）

进阶三（了解即可）：
  □ RabbitMQ 集群（镜像队列 / Quorum Queue）
  □ 消息追踪（Firehose / rabbitmq_tracing）
  □ 与 Spring Cloud Stream 整合
```
