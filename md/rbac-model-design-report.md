# RBAC 权限模型设计详细报告

> 对应项目：`cheer.demo`（Spring Boot 3.5 + MyBatis-Plus + MySQL）
> 本文档是 RBAC 权限模型的完整设计说明，涵盖模型原理、表结构设计、权限流转、
> 树形菜单设计、权限字符串规范、主键策略选型、逻辑删除等全部设计决策。

---

## 目录

- [第一章：RBAC 模型概述](#第一章rbac-模型概述)
  - [1.1 什么是 RBAC](#11-什么是-rbac)
  - [1.2 RBAC 的发展历程（RBAC0 → RBAC1 → RBAC2 → RBAC3）](#12-rbac-的发展历程rbac0--rbac1--rbac2--rbac3)
  - [1.3 为什么企业项目几乎都用 RBAC](#13-为什么企业项目几乎都用-rbac)
  - [1.4 本项目的 RBAC 模型定位](#14-本项目的-rbac-模型定位)
- [第二章：核心实体与关系](#第二章核心实体与关系)
  - [2.1 五张表全景](#21-五张表全景)
  - [2.2 ER 关系图](#22-er-关系图)
  - [2.3 多对多关系拆解](#23-多对多关系拆解)
  - [2.4 权限流转链路](#24-权限流转链路)
- [第三章：用户表（sys_user）设计](#第三章用户表sys_user设计)
  - [3.1 表结构](#31-表结构)
  - [3.2 字段设计决策](#32-字段设计决策)
  - [3.3 索引设计](#33-索引设计)
  - [3.4 用户表的扩展方向](#34-用户表的扩展方向)
- [第四章：角色表（sys_role）设计](#第四章角色表sys_role设计)
  - [4.1 表结构](#41-表结构)
  - [4.2 role_name vs role_key 的区别](#42-role_name-vs-role_key-的区别)
  - [4.3 为什么角色也需要 status 和 del_flag](#43-为什么角色也需要-status-和-del_flag)
  - [4.4 预置角色设计](#44-预置角色设计)
- [第五章：菜单/权限表（sys_menu）设计](#第五章菜单权限表sys_menu设计)
  - [5.1 表结构](#51-表结构)
  - [5.2 为什么"菜单"和"权限"合在一张表](#52-为什么菜单和权限合在一张表)
  - [5.3 menu_type 的三种类型详解](#53-menu_type-的三种类型详解)
  - [5.4 树形结构的自引用设计](#54-树形结构的自引用设计)
  - [5.5 权限字符串（perms）设计规范](#55-权限字符串perms设计规范)
  - [5.6 一棵完整的菜单树示例](#56-一棵完整的菜单树示例)
  - [5.7 菜单树的存储与查询对比](#57-菜单树的存储与查询对比)
- [第六章：中间表设计](#第六章中间表设计)
  - [6.1 sys_user_role 表结构](#61-sys_user_role-表结构)
  - [6.2 sys_role_menu 表结构](#62-sys_role_menu-表结构)
  - [6.3 为什么中间表不需要通用字段](#63-为什么中间表不需要通用字段)
  - [6.4 为什么中间表不建单独的 id 主键](#64-为什么中间表不建单独的-id-主键)
  - [6.5 联合主键的查询方式](#65-联合主键的查询方式)
- [第七章：主键策略选型](#第七章主键策略选型)
  - [7.1 三种主键策略对比](#71-三种主键策略对比)
  - [7.2 为什么本项目选雪花算法](#72-为什么本项目选雪花算法)
  - [7.3 雪花算法的原理](#73-雪花算法的原理)
  - [7.4 雪花 ID 在实际开发中的注意事项](#74-雪花-id-在实际开发中的注意事项)
- [第八章：逻辑删除设计](#第八章逻辑删除设计)
  - [8.1 物理删除 vs 逻辑删除](#81-物理删除-vs-逻辑删除)
  - [8.2 del_flag 的两种常用方案](#82-del_flag-的两种常用方案)
  - [8.3 本项目的选择及原因](#83-本项目的选择及原因)
  - [8.4 逻辑删除在 MyBatis-Plus 中的实现](#84-逻辑删除在-mybatis-plus-中的实现)
  - [8.5 逻辑删除的副作用与应对](#85-逻辑删除的副作用与应对)
- [第九章：通用字段设计](#第九章通用字段设计)
  - [9.1 create_time / update_time](#91-create_time--update_time)
  - [9.2 为什么用 DATETIME 而不是 TIMESTAMP](#92-为什么用-datetime-而不是-timestamp)
  - [9.3 自动填充 vs 数据库 DEFAULT](#93-自动填充-vs-数据库-default)
- [第十章：RBAC 在系统各阶段的体现](#第十章rbac-在系统各阶段的体现)
  - [10.1 阶段 1-2：数据层就绪](#101-阶段-1-2数据层就绪)
  - [10.2 阶段 3：菜单树与动态菜单](#102-阶段-3菜单树与动态菜单)
  - [10.3 阶段 4：认证授权](#103-阶段-4认证授权)
  - [10.4 阶段 5：缓存加速](#104-阶段-5缓存加速)
- [第十一章：RBAC 扩展方向](#第十一章rbac-扩展方向)
  - [11.1 数据权限（行级权限）](#111-数据权限行级权限)
  - [11.2 多租户](#112-多租户)
  - [11.3 动态权限（运行时权限）](#113-动态权限运行时权限)

---

# 第一章：RBAC 模型概述

## 1.1 什么是 RBAC

RBAC（Role-Based Access Control，基于角色的访问控制）是一种**通过"角色"作为中介来管理权限**的模型。它的核心思想是：

```
不要直接把权限分配给用户，而是把权限分配给角色，再把角色分配给用户。
```

用一个现实类比：

```
没有 RBAC（直接给每个人分配权限）：
  张三 → 可以看用户列表、可以新增用户、可以删除用户
  李四 → 可以看用户列表、可以新增用户
  王五 → 可以看用户列表、可以看角色列表
  ...（每个新来的人都要逐一分配权限，几百个用户管理起来很痛苦）

有 RBAC（通过角色间接分配）：
  超级管理员角色 → 可以看用户列表、可以新增用户、可以删除用户、可以看角色列表...
  普通用户角色   → 可以看用户列表、可以新增用户
  只读用户角色   → 可以看用户列表

  张三 → 超级管理员角色
  李四 → 普通用户角色
  王五 → 只读用户角色
  ...（新来的人只需要分配一个角色，权限跟着角色走）
```

**好处：权限管理和用户管理解耦。** 权限变动时（比如"普通用户"需要增加一个"导出"权限），只需要改角色的权限配置，所有"普通用户"角色的人自动生效，不需要逐个改。

## 1.2 RBAC 的发展历程（RBAC0 → RBAC1 → RBAC2 → RBAC3）

RBAC 不是一步到位的，它经历了多个版本演进：

### RBAC0（基础模型）

```
用户 ←→ 角色 ←→ 权限
（多对多）（多对多）
```

最简单的版本。用户可以有多个角色，角色可以有多个权限。**本项目就是这个模型。**

### RBAC1（角色继承）

```
经理角色  ←继承←  组长角色  ←继承←  普通员工角色
  │               │                │
  拥有所有下级权限 + 自己独有权限
```

角色之间有了层级关系——上级角色自动继承下级角色的所有权限。比如"经理"继承了"组长"和"普通员工"的所有权限，不需要重复分配。

**应用场景：** 大型企业里，管理层级和权限层级经常是对应的。

### RBAC2（角色约束）

在 RBAC0 的基础上增加了约束规则：

| 约束类型 | 含义 | 示例 |
|---|---|---|
| 互斥角色 | 某两个角色不能同时分配给同一个人 | "出纳"和"审计"不能同一个人 |
| 基数约束 | 一个角色最多/最少分配给多少人 | "超级管理员"最多 2 人 |
| 先决角色 | 拥有角色 B 的前提是先拥有角色 A | "部门经理"的前提是"员工" |
| 运行时互斥 | 同一个会话中不能同时激活两个互斥角色 | "普通用户"和"管理员"不能同时激活 |

**应用场景：** 金融、银行等对权限控制要求极严格的系统。

### RBAC3（RBAC1 + RBAC2 的统一模型）

结合了角色继承和角色约束，最完整的 RBAC 模型。但复杂度也最高，绝大多数企业项目用不到这个级别。

### 本项目的定位

**RBAC0（基础模型），未来可扩展到 RBAC1（如果需要角色继承）或 RBAC2 的互斥角色。** 先把基础的做扎实，再加约束。

## 1.3 为什么企业项目几乎都用 RBAC

| 对比项 | 直接给用户分配权限 | RBAC（通过角色） |
|---|---|---|
| 管理方式 | 每个用户单独配置 | 按角色批量配置 |
| 新员工入职 | 逐一分配所有权限 | 分配一个角色即可 |
| 权限变更 | 逐个修改所有受影响用户 | 改角色权限，所有人自动生效 |
| 权限回收（离职） | 逐一删除 | 解除角色绑定 |
| 审计追踪 | 难以追踪"谁有什么权限" | 清晰：看角色的权限配置 |
| 复杂度 | 用户少时简单，用户多时混乱 | 初始需要设计角色体系，但后期维护轻松 |

**结论：** RBAC 的前期成本（设计角色体系）换来的是后期极低的维护成本。用户越多、权限越复杂，RBAC 的优势越明显。

## 1.4 本项目的 RBAC 模型定位

```
用户（sys_user）
  │
  ├── 多对多 ──→ 角色（sys_role）── 多对多 ──→ 菜单/权限（sys_menu）
  │
  └── 通过 sys_user_role 关联    通过 sys_role_menu 关联
```

**三张核心表 + 两张中间表 = 五张表。**

- 核心表：`sys_user`（用户）、`sys_role`（角色）、`sys_menu`（菜单/权限）
- 中间表：`sys_user_role`（用户-角色关联）、`sys_role_menu`（角色-菜单关联）

---

# 第二章：核心实体与关系

## 2.1 五张表全景

| 表名 | 类型 | 存什么 | 核心字段 |
|---|---|---|---|
| `sys_user` | 核心表 | 系统用户 | id, username, password, nickname, status |
| `sys_role` | 核心表 | 角色 | id, role_name, role_key, status |
| `sys_menu` | 核心表 | 菜单/权限（树形） | id, parent_id, menu_name, menu_type, perms |
| `sys_user_role` | 中间表 | 用户与角色的多对多关系 | user_id, role_id |
| `sys_role_menu` | 中间表 | 角色与菜单的多对多关系 | role_id, menu_id |

## 2.2 ER 关系图

```
┌──────────────┐         ┌──────────────┐         ┌──────────────┐
│   sys_user   │         │   sys_role    │         │   sys_menu    │
│──────────────│         │──────────────│         │──────────────│
│ id (PK)      │         │ id (PK)      │         │ id (PK)      │
│ username     │         │ role_name    │         │ parent_id(FK)│──→ 自引用
│ password     │         │ role_key     │         │ menu_name    │
│ nickname     │         │ sort         │         │ menu_type    │
│ status       │         │ status       │         │ perms        │
│ del_flag     │         │ del_flag     │         │ sort         │
│ create_time  │         │ create_time  │         │ status       │
│ update_time  │         │ update_time  │         │ del_flag     │
└──────┬───────┘         └──────┬───────┘         │ create_time  │
       │                        │                 │ update_time  │
       │                        │                 └──────────────┘
       │    ┌──────────────┐    │
       │    │ sys_user_role│    │
       │    │──────────────│    │
       └────│ user_id (PK) │    │
            │ role_id (PK) │────┘
            └──────────────┘
                                ┌──────────────┐
                                │ sys_role_menu│
                                │──────────────│
                                │ role_id (PK) │──── sys_role.id
                                │ menu_id (PK) │──── sys_menu.id
                                └──────────────┘
```

**关系解读：**
- `sys_user` ↔ `sys_role`：**多对多**（一个用户多个角色，一个角色多个用户）
- `sys_role` ↔ `sys_menu`：**多对多**（一个角色多个菜单/权限，一个菜单/权限可以被多个角色拥有）
- `sys_menu` 自引用：**一对多**（一个菜单有多个子菜单，通过 `parent_id` 自关联）

## 2.3 多对多关系拆解

### 用户 ↔ 角色（多对多）

```
用户 A ────────────────────────┐
用户 B ──── sys_user_role ─────┤─── admin 角色 ──── sys_role_menu ──── 菜单 1,2,3,4,5
用户 C ────────────────────────┘
用户 B ──── sys_user_role ─────┘─── common 角色 ─── sys_role_menu ──── 菜单 1,2,3
```

**不建多对多的方式：**
- 方式一：在 `sys_user` 里加 `role_ids VARCHAR(255)` 存逗号分隔的角色 ID（如 `"1,2,3"`）→ **不推荐**，违反第一范式，查询时无法用索引
- 方式二：建中间表 `sys_user_role` → **推荐**，标准做法，查询灵活，支持索引

### 角色 ↔ 菜单/权限（多对多）

同上，用中间表 `sys_role_menu` 实现多对多。

### 为什么必须是多对多而不是一对多

```
如果"一个用户只能有一个角色"（一对多）：
  张三是"管理员"，他只有管理员的所有权限。
  如果还需要给张三一个"普通用户"的额外权限怎么办？
  → 只能再建一个"管理员+普通用户"的混合角色 → 角色爆炸。

多对多的好处：
  张三可以同时拥有"管理员"和"普通用户"两个角色，权限自动取并集。
```

## 2.4 权限流转链路

一个请求的权限判断，要经过完整的链路：

```
1. 请求到达 → JWT 过滤器 → 从 Token 解析出 userId
                  │
                  ▼
2. userId → sys_user_role → 拿到 roleIds
                  │
                  ▼
3. roleIds → sys_role_menu → 拿到 menuIds
                  │
                  ▼
4. menuIds → sys_menu → 拿到 perms 权限列表
   例如：["system:user:list", "system:user:add", "system:role:list"]
                  │
                  ▼
5. @PreAuthorize("hasAuthority('system:user:add')")
   → 检查权限列表里有没有这个字符串
   → 有 → 放行
   → 没有 → 403 Forbidden
```

**优化：** 步骤 2-4 在"登录"时执行一次，结果塞进 JWT 的 Payload。之后每次请求只从 Token 里读，不再查数据库。这就是 JWT 无状态认证的核心优势。

---

# 第三章：用户表（sys_user）设计

## 3.1 表结构

```sql
CREATE TABLE sys_user (
    id          BIGINT      NOT NULL COMMENT '用户ID',
    username    VARCHAR(64) NOT NULL COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL COMMENT '密码（BCrypt加密）',
    nickname    VARCHAR(64) NOT NULL COMMENT '用户昵称',
    avatar      VARCHAR(255) DEFAULT NULL COMMENT '头像URL',
    email       VARCHAR(128) DEFAULT NULL COMMENT '邮箱',
    phone       VARCHAR(20)  DEFAULT NULL COMMENT '手机号',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT     NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
);
```

## 3.2 字段设计决策

### username（登录账号）

- `VARCHAR(64)`：64 字符足够覆盖大多数登录名
- `NOT NULL` + `UNIQUE`：登录账号不能为空且不能重复
- **不叫 `name` 或 `account`**：`username` 是业界标准命名（若依、Spring Security 的 `UserDetailsService` 也用 username）

### password（密码）

- `VARCHAR(100)`：BCrypt 加密后的密码通常是 60 个字符，100 留足余量
- `NOT NULL`：密码不能为空
- **存的是 BCrypt 加密后的字符串**，不是明文。例如：`$2a$10$N.zmdr9k7uOCQb376NoUnuTJ8iAt6Z5EHsM8lE9lBOsl7iAt6Z5EH`

### nickname（昵称）

- `VARCHAR(64) NOT NULL`：昵称不能为空
- **和 username 分开**：username 是登录用的（不能改），nickname 是显示用的（可以改）

### avatar / email / phone

- `DEFAULT NULL`：可选信息，允许为空
- 不设 `NOT NULL`：不是每个用户都必须填头像/邮箱/手机号

### status（状态）

- `TINYINT NOT NULL DEFAULT 1`：默认启用
- `1 = 启用`，`0 = 禁用`
- **禁用的用户不能登录**（登录逻辑里要检查 status）

## 3.3 索引设计

| 索引 | 类型 | 原因 |
|---|---|---|
| `PRIMARY KEY (id)` | 主键索引 | 聚簇索引，所有查询都依赖 |
| `UNIQUE KEY uk_username (username)` | 唯一索引 | 保证登录名不重复；登录时按 username 查用户是最高频操作，必须有索引 |

**没有给 status / del_flag 加索引的原因：** 目前这两个字段不会单独出现在 WHERE 条件里（通常是 `WHERE username = ?`，不在 username 以外的字段上建索引）。如果将来有"查询所有禁用用户"的需求，可以再加。

## 3.4 用户表的扩展方向

| 扩展字段 | 用途 | 何时加 |
|---|---|---|
| `dept_id BIGINT` | 部门归属（数据权限的基础） | 做数据权限时 |
| `last_login_time DATETIME` | 最后登录时间 | 登录日志功能 |
| `login_ip VARCHAR(128)` | 最后登录 IP | 安全审计 |
| `remark VARCHAR(500)` | 备注 | 按需 |

---

# 第四章：角色表（sys_role）设计

## 4.1 表结构

```sql
CREATE TABLE sys_role (
    id          BIGINT      NOT NULL COMMENT '角色ID',
    role_name   VARCHAR(64) NOT NULL COMMENT '角色名称',
    role_key    VARCHAR(64) NOT NULL COMMENT '角色权限字符串',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_key (role_key)
);
```

## 4.2 role_name vs role_key 的区别

这两个字段经常被混淆，但它们的用途完全不同：

| 字段 | 给谁看的 | 用途 | 示例 |
|---|---|---|---|
| `role_name` | **给人看的** | 在角色管理界面上显示 | "系统管理员"、"普通用户" |
| `role_key` | **给代码用的** | 在 Java 代码里做权限判断 | `admin`、`common` |

**为什么需要 role_key？**

场景：你要在代码里判断"当前用户是不是管理员"：

```java
// 用 role_key 判断（✅ 推荐）
if (currentUser.getRoleKey().equals("admin")) {
    // 管理员特殊逻辑
}

// 用 role_name 判断（❌ 不推荐）
if (currentUser.getRoleName().equals("系统管理员")) {
    // 如果有人把角色名改成"超级管理员"，这段代码就失效了
}
```

`role_key` 是稳定的代码标识符，不应该随意改动。`role_name` 是给人看的展示名，可以随时改。

**类比：** `role_key` 是变量名（`isAdmin`），`role_name` 是变量的显示值（"系统管理员"）。你不会在代码里用中文做变量名。

## 4.3 为什么角色也需要 status 和 del_flag

| 场景 | 不用 status/del_flag | 用 status/del_flag |
|---|---|---|
| 暂时禁用某个角色 | 只能删除角色 → 但删除后角色关联数据就断了 | `status=0` → 角色保留但临时禁用 |
| 回收某人的某个角色 | 没有中间状态，要么有要么没有 | 先禁用角色，等确认后再删除 |
| 角色管理界面 | 只能看到"所有角色"或"已删除角色"二选一 | 可以展示"启用/禁用"两种状态 |

**"软删除"理念贯穿整个设计**：能不真删就不真删。保留数据比丢失数据好。

## 4.4 预置角色设计

企业系统通常预置两个角色：

| role_key | role_name | 权限范围 |
|---|---|---|
| `admin` | 超级管理员 | 全部菜单/权限 |
| `common` | 普通用户 | 部分菜单/权限 |

系统初始化时通过 SQL 种子数据插入这两个角色，不通过界面创建。

---

# 第五章：菜单/权限表（sys_menu）设计

这是 RBAC 模型里**最复杂也最关键的一张表**。它同时承担两个职责：
1. **菜单管理**：控制前端能看到哪些导航、哪些页面
2. **权限管理**：控制后端接口能不能被调用

## 5.1 表结构

```sql
CREATE TABLE sys_menu (
    id          BIGINT      NOT NULL COMMENT '菜单/权限ID',
    parent_id   BIGINT      NOT NULL DEFAULT 0 COMMENT '父菜单ID，0表示顶级',
    menu_name   VARCHAR(64) NOT NULL COMMENT '菜单/按钮名称',
    menu_type   CHAR(1)     NOT NULL COMMENT '类型：M=目录 C=菜单 F=按钮',
    path        VARCHAR(200) DEFAULT NULL COMMENT '前端路由地址',
    component   VARCHAR(255) DEFAULT NULL COMMENT '前端组件路径',
    perms       VARCHAR(100) DEFAULT NULL COMMENT '权限标识',
    icon        VARCHAR(100) DEFAULT NULL COMMENT '菜单图标',
    sort        INT         NOT NULL DEFAULT 0 COMMENT '显示顺序',
    status      TINYINT     NOT NULL DEFAULT 1 COMMENT '状态：1=启用 0=禁用',
    create_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    del_flag    TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_parent_id (parent_id)
);
```

## 5.2 为什么"菜单"和"权限"合在一张表

你可能会问：为什么不建两张表——一张存菜单（给前端用），一张存权限（给后端用）？

**因为菜单和权限在 RBAC 模型里是同源的。** 看这个例子：

```
"用户管理"这个菜单 → 对应"system:user:list"这个权限 → 绑定在角色上
"用户新增"这个按钮 → 对应"system:user:add"这个权限 → 绑定在角色上
```

菜单决定了"前端展示什么"，权限决定了"后端允许什么"。它们是**同一个概念的两个面**——都是"角色能做什么"的具体化。

如果拆成两张表，你需要维护两套"角色关联"，而且要保证菜单和权限的同步：删除一个菜单时，要同时删除对应的权限。合在一张表里，一个 `del_flag=1` 就搞定了。

**若依就是这么做的——sys_menu 同时承担菜单和权限两个职责。** 这是国内企业级后台管理系统的标准做法。

## 5.3 menu_type 的三种类型详解

| 类型 | 值 | 含义 | 有 path？ | 有 component？ | 有 perms？ | 有 icon？ |
|---|---|---|---|---|---|---|
| **目录** | `M` (Menu) | 侧边栏的一级分组 | ✅ (路由前缀) | ✅ (通常为 `Layout`) | ❌ 通常无 | ✅ |
| **菜单** | `C` (Component) | 可点击进入的页面 | ✅ (路由路径) | ✅ (页面组件路径) | ✅ | ✅ |
| **按钮** | `F` (Function) | 页面内的操作按钮 | ❌ | ❌ | ✅ | ❌ |

### 用图理解三种类型的关系

```
侧边栏导航（前端渲染）

├── 🏠 系统管理（M 目录）
│   │  path="/system"    component="Layout"    icon="system"
│   │
│   ├── 👤 用户管理（C 菜单）
│   │  │  path="user"    component="system/user/index"    perms="system:user:list"    icon="user"
│   │  │
│   │  ├── [新增] (F 按钮)
│   │  │  perms="system:user:add"
│   │  │
│   │  ├── [编辑] (F 按钮)
│   │  │  perms="system:user:edit"
│   │  │
│   │  └── [删除] (F 按钮)
│   │     perms="system:user:delete"
│   │
│   └── 🔑 角色管理（C 菜单）
│      path="role"    component="system/role/index"    perms="system:role:list"    icon="role"

└── 📄 首页（C 菜单）
   path="home"    component="home"    icon="home"
```

### 三种类型对应的三种权限控制

| 类型 | 前端控制 | 后端控制 |
|---|---|---|
| M（目录） | 是否显示这个侧边栏分组 | 通常不需要（目录本身不对应具体接口） |
| C（菜单） | 是否显示这个侧边栏菜单项 + 动态路由 | `hasAuthority('system:user:list')` 控制能否查看列表 |
| F（按钮） | 是否显示这个操作按钮 | `hasAuthority('system:user:add')` 控制能否执行操作 |

**按钮（F）是权限控制的精细粒度。** 前端不显示"新增"按钮 + 后端 `@PreAuthorize` 拦截，双重保护。

## 5.4 树形结构的自引用设计

`sys_menu` 通过 `parent_id` 字段实现树形结构——**每一行引用同表里的另一行作为自己的父节点**。

```
id=2001  parent_id=0     ← 顶级（没有父节点）
id=2002  parent_id=2001  ← 2001 的子节点
id=2003  parent_id=2002  ← 2002 的子节点（2001 的孙节点）
```

### 为什么 parent_id 用 `0` 而不是 `NULL`

| 做法 | SQL 写法 | Java 判断 |
|---|---|---|
| `NULL` | `WHERE parent_id IS NULL`（特殊语法） | `Objects.isNull(parentId)` |
| `0` | `WHERE parent_id = 0`（统一） | `parentId == 0L` |

`0` 更简洁，而且 `id=0` 的菜单不存在，所以 `0` 天然就是"无父级"的语义标记。

### 为什么只建普通索引而不是外键约束

```sql
KEY idx_parent_id (parent_id)    -- 普通索引，不是 FOREIGN KEY
```

**企业项目通常不建外键约束。** 原因：

1. **性能**：外键约束在插入/更新/删除时会触发额外检查（检查引用完整性），影响性能
2. **灵活性**：删除父节点时，外键约束默认阻止删除或级联删除——但菜单树可能需要先删子节点再删父节点，或者只是"软删除"（del_flag=1），外键约束会干扰
3. **分库分表不友好**：外键要求两张表在同一个数据库里，分库分表后外键失效

**引用完整性由应用层保证**（代码里先删子节点再删父节点），不依赖数据库约束。

## 5.5 权限字符串（perms）设计规范

权限字符串存放在 `sys_menu.perms` 字段里，是**后端 @PreAuthorize 注解直接使用的值**。

### 命名规范：`模块:资源:操作`

```
system:user:list       ← 模块:资源:操作
system:user:add
system:user:edit
system:user:delete
system:user:export
system:role:list
system:role:add
system:role:edit
system:role:delete
system:menu:list
system:menu:add
system:menu:edit
monitor:online:list
monitor:online:forceLogout
```

### 为什么用 `:` 分隔的三段式

1. **可读性**：`system:user:add` 比 `userAddPermission` 或 `1_2_3` 直观
2. **可扩展**：可以用 `hasAuthority('system:user:*')` 匹配某资源下所有操作（需要自定义实现，但结构上支持）
3. **业界标准**：若依、Spring Security 官方文档都用这种格式

### 谁需要 perms，谁不需要

| menu_type | perms | 原因 |
|---|---|---|
| M（目录） | `NULL` | 目录不对应具体接口，没有需要控制的权限 |
| C（菜单） | 有 | 菜单对应"查看列表"接口，需要控制谁能看到 |
| F（按钮） | 有 | 按钮对应"新增/编辑/删除"接口，需要控制谁能操作 |

**注意：** 不是所有菜单和按钮都需要 perms。如果一个页面不需要权限控制（比如"首页"），perms 就为 NULL。

## 5.6 一棵完整的菜单树示例

以一个中型后台管理系统为例：

```
├── 🏠 系统管理（M）id=1
│   ├── 👤 用户管理（C）id=2  perms=system:user:list
│   │   ├── [新增]（F）id=11 perms=system:user:add
│   │   ├── [编辑]（F）id=12 perms=system:user:edit
│   │   ├── [删除]（F）id=13 perms=system:user:delete
│   │   ├── [重置密码]（F）id=14 perms=system:user:resetPwd
│   │   └── [导出]（F）id=15 perms=system:user:export
│   │
│   ├── 🔑 角色管理（C）id=3  perms=system:role:list
│   │   ├── [新增]（F）id=31 perms=system:role:add
│   │   ├── [编辑]（F）id=32 perms=system:role:edit
│   │   ├── [删除]（F）id=33 perms=system:role:delete
│   │   └── [分配权限]（F）id=34 perms=system:role:assignPermission
│   │
│   ├── 📋 菜单管理（C）id=4  perms=system:menu:list
│   │   ├── [新增]（F）id=41 perms=system:menu:add
│   │   ├── [编辑]（F）id=42 perms=system:menu:edit
│   │   └── [删除]（F）id=43 perms=system:menu:delete
│   │
│   └── 📊 部门管理（C）id=5  perms=system:dept:list
│       ├── [新增]（F）id=51 perms=system:dept:add
│       └── [编辑]（F）id=52 perms=system:dept:edit
│
├── 🔍 系统监控（M）id=10
│   ├── 📈 在线用户（C）id=20  perms=monitor:online:list
│   │   └── [强退]（F）id=201 perms=monitor:online:forceLogout
│   │
│   └── 📝 操作日志（C）id=21  perms=monitor:log:list
│       ├── [删除]（F）id=211 perms=monitor:log:delete
│       └── [清空]（F）id=212 perms=monitor:log:clean
│
└── 📄 首页（C）id=100  path=home  component=home
```

**统计：** 10 个菜单(C)、10 个按钮(F)、2 个目录(M) = 22 条菜单记录。每个有 perms 的条目都对应一个后端接口的权限控制点。

## 5.7 菜单树的存储与查询对比

| 操作 | SQL | MyBatis-Plus |
|---|---|---|
| 查全量菜单 | `SELECT * FROM sys_menu WHERE del_flag=0 ORDER BY sort` | `selectList(wrapper.orderByAsc(Menu::getSort))` |
| 查某个用户的菜单 | 四步链路（userId→roleIds→menuIds→menus） | 见接口 B 实现 |
| 新增菜单 | `INSERT INTO sys_menu(...) VALUES(...)` | `menuMapper.insert(menu)` |
| 修改菜单 | `UPDATE sys_menu SET ... WHERE id=?` | `menuMapper.updateById(menu)` |
| 删除菜单 | `UPDATE sys_menu SET del_flag=1 WHERE id=?`（逻辑删除） | `menuMapper.deleteById(id)` + `@TableLogic` |
| 构建树形结构 | 不在 SQL 里做，在 Java 里递归/分组 | `buildTree()` 方法 |

---

# 第六章：中间表设计

## 6.1 sys_user_role 表结构

```sql
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    PRIMARY KEY (user_id, role_id)
);
```

**只有两列，没有 id、没有时间、没有 del_flag。** 极度精简。

## 6.2 sys_role_menu 表结构

```sql
CREATE TABLE sys_role_menu (
    role_id BIGINT NOT NULL COMMENT '角色ID',
    menu_id BIGINT NOT NULL COMMENT '菜单ID',
    PRIMARY KEY (role_id, menu_id)
);
```

同样极度精简。

## 6.3 为什么中间表不需要通用字段

核心表（sys_user / sys_role / sys_menu）有 `create_time / update_time / del_flag`，中间表没有。原因：

### 不需要 create_time / update_time

中间表是**关系记录**——"用户 A 有角色 B"这条关系本身没有"创建时间"或"修改时间"的语义。你关心的是"现在有没有这个关系"，不关心"什么时候建立的"。

### 不需要 del_flag

中间表的"删除"就是**物理删除**（DELETE 整行）：

```sql
-- 删除用户的某个角色
DELETE FROM sys_user_role WHERE user_id = 1 AND role_id = 1002;
```

为什么中间表可以物理删除，而核心表不行？

| | 核心表 | 中间表 |
|---|---|---|
| 删除后数据是否有价值 | 有（用户/角色/菜单的历史记录重要） | 无（"用户 A 曾经有过角色 B"的记录几乎没用） |
| 删除后是否影响其他表 | 是（删用户后 sys_user_role 里的引用悬空） | 不影响（删 sys_user_role 不影响 sys_user/sys_role 本身） |
| 是否有外键引用 | 是（其他表可能引用用户 ID） | 不影响（中间表只是"关系"） |

**简单说：中间表是"纯关系表"，删了就删了，没有任何副作用。**

## 6.4 为什么中间表不建单独的 id 主键

```sql
-- ❌ 不推荐（多一列没用）
CREATE TABLE sys_user_role (
    id      BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    UNIQUE KEY uk_user_role (user_id, role_id)
);

-- ✅ 推荐（联合主键）
CREATE TABLE sys_user_role (
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    PRIMARY KEY (user_id, role_id)
);
```

联合主键 `(user_id, role_id)` 本身就保证了"一个用户不能重复分配同一个角色"。如果加了自增 id，多一列存储空间不说，`user_id + role_id` 的唯一约束还需要额外维护——联合主键已经天然解决了这个问题。

## 6.5 联合主键的查询方式

```sql
-- 查某个用户的所有角色
SELECT role_id FROM sys_user_role WHERE user_id = 1;

-- 查某个角色的所有用户
SELECT user_id FROM sys_user_role WHERE role_id = 1001;

-- 给用户分配角色
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1001);

-- 取消用户的某个角色
DELETE FROM sys_user_role WHERE user_id = 1 AND role_id = 1002;
```

MyBatis-Plus 对联合主键的处理：实体不加 `@TableId`，MP 会把它当作普通表处理，用 Wrapper 构造查询即可。

---

# 第七章：主键策略选型

## 7.1 三种主键策略对比

| 策略 | SQL | 优点 | 缺点 |
|---|---|---|---|
| **自增 ID** | `id BIGINT AUTO_INCREMENT` | 简单；天然有序 | 分布式下主键冲突；暴露业务量（id=10000 → 大概有 10000 条数据）；插入有锁竞争（自增锁） |
| **UUID** | `id VARCHAR(36)` | 全局唯一；无需协调 | 字符串存储空间大（36 字节 vs BIGINT 的 8 字节）；无序导致索引页分裂；不可排序 |
| **雪花算法** | `id BIGINT`（应用层生成） | 全局唯一；趋势递增（对索引友好）；不暴露业务量；无锁竞争 | 依赖机器时钟（时钟回拨会重复） |

## 7.2 为什么本项目选雪花算法

```
1. 学习价值 —— MyBatis-Plus 的默认策略就是雪花算法，用它能自然学会
2. 企业主流 —— 若依、芋道等主流开源项目都用雪花 ID
3. 未来友好 —— 如果项目拆微服务（阶段 10），分布式 ID 不会冲突
4. MyBatis-Plus 零配置 —— @TableId(type = IdType.ASSIGN_ID) 一行注解搞定
```

## 7.3 雪花算法的原理

一个 64 位的 BIGINT 雪花 ID 由以下部分组成：

```
┌──────────────────┬──────────┬──────────────────────┬──────────┐
│  1 bit (符号位)   │ 41 bits  │      10 bits         │ 12 bits  │
│  永远是 0         │ 时间戳   │  机器/数据中心 ID    │  序列号   │
│                  │  毫秒级   │                      │  同一毫秒内│
└──────────────────┴──────────┴──────────────────────┴──────────┘
```

| 部分 | 位数 | 含义 |
|---|---|---|
| 符号位 | 1 bit | 永远是 0（保证 ID 是正数） |
| 时间戳 | 41 bits | 从某个基准时间（如 2020-01-01）到现在的毫秒数，可用约 69 年 |
| 机器 ID | 10 bits | 最多 1024 个节点（可配置数据中心 ID + 机器 ID） |
| 序列号 | 12 bits | 同一毫秒内的递增序号，最多 4096 个/毫秒 |

**特点：**
- **趋势递增**——时间戳在高位，新 ID 比旧 ID 大（对 B+ 树索引友好，减少页分裂）
- **分布式唯一**——不同机器有不同的机器 ID，不会冲突
- **高性能**——不依赖数据库，应用层本地生成

## 7.4 雪花 ID 在实际开发中的注意事项

| 问题 | 说明 |
|---|---|
| 时钟回拨 | 如果服务器时钟往回调，可能生成重复 ID。生产环境要配置时钟回拨检测 |
| ID 的视觉效果 | 雪花 ID 是 19 位数字（如 1892345678901234567），人读性差。不影响功能，调试时可能不方便 |
| 存储空间 | BIGINT 占 8 字节，比 INT（4 字节）多一倍，但存储成本可以忽略 |
| 前端兼容 | JavaScript 的 `Number.MAX_SAFE_INTEGER` 是 2^53 - 1，超过这个值会丢失精度。雪花 ID 可能超过这个值，前端要用 `String` 接收 |

---

# 第八章：逻辑删除设计

## 8.1 物理删除 vs 逻辑删除

| | 物理删除 | 逻辑删除 |
|---|---|---|
| SQL | `DELETE FROM sys_user WHERE id=1` | `UPDATE sys_user SET del_flag=1 WHERE id=1` |
| 数据 | 从磁盘上消失 | 仍然在磁盘上，只是标记为"已删除" |
| 可恢复 | ❌ 不可恢复 | ✅ 改回 `del_flag=0` 即可恢复 |
| 外键影响 | 引用此行的其他表会出问题 | 不影响（查询时自动过滤已删除数据） |
| 存储成本 | 无额外成本 | 多占一个字段的空间 |

## 8.2 del_flag 的两种常用方案

| 方案 | 值 | 优点 | 缺点 |
|---|---|---|---|
| 0/1 | `0=正常 1=已删除` | 直观 | 只有两种状态 |
| 时间戳 | 正常=NULL，删除=当前时间戳 | 知道什么时候删的 | 查询时要多判断 NULL |

**本项目选 0/1 方案**——简单直观，若依也用这种。

## 8.3 本项目的选择及原因

```sql
del_flag TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除：0=正常 1=已删除'
```

选择原因：

1. **安全**——误删可恢复（`UPDATE SET del_flag=0`）
2. **数据完整性**——删除用户后，sys_user_role 里的引用不会悬空（查询时自动过滤 del_flag=0）
3. **审计追踪**——可以查到"被删除的用户"（虽然标记为删除，但数据还在）
4. **MyBatis-Plus 原生支持**——`@TableLogic` 注解一行搞定，查询自动过滤

## 8.4 逻辑删除在 MyBatis-Plus 中的实现

```java
@TableLogic
private Integer delFlag;
```

加了 `@TableLogic` 后，MP 自动：

| 操作 | MP 生成的 SQL |
|---|---|
| `selectList(null)` | `SELECT ... FROM sys_user WHERE del_flag=0`（自动追加） |
| `selectById(1)` | `SELECT ... FROM sys_user WHERE id=1 AND del_flag=0`（自动追加） |
| `deleteById(1)` | `UPDATE sys_user SET del_flag=1 WHERE id=1 AND del_flag=0`（改成 UPDATE） |
| `updateById(user)` | `UPDATE sys_user SET ... WHERE id=1 AND del_flag=0`（不改 del_flag） |

**你不需要手写 `del_flag=0` 条件**——MP 在所有查询里自动加上。

## 8.5 逻辑删除的副作用与应对

### 副作用 1：username 唯一约束冲突

```
用户 A（username="admin"）被逻辑删除（del_flag=1）
新建用户 B（username="admin"）→ 报错：UNIQUE KEY 冲突
```

因为逻辑删除的数据还在表里，UNIQUE 约束仍然生效。

**解决方案：** 把唯一索引改成联合唯一索引，包含 `del_flag`：

```sql
UNIQUE KEY uk_username_del (username, del_flag)
```

这样 "admin + 0" 和 "admin + 1" 是两条不同的记录，不冲突。

**本项目暂未做此优化**——因为目前测试数据量小，遇到实际冲突时再加。

### 副作用 2：统计查询需要排除已删除数据

```sql
-- 如果用了 @TableLogic，selectCount 会自动过滤
SELECT COUNT(*) FROM sys_user WHERE del_flag=0;
```

**@TableLogic 已经帮你处理了**，不需要额外操心。

---

# 第九章：通用字段设计

## 9.1 create_time / update_time

```sql
create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
update_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
```

- `create_time`：记录创建时间，插入时自动填充（`DEFAULT CURRENT_TIMESTAMP`）
- `update_time`：记录最后修改时间，每次 UPDATE 自动更新（`ON UPDATE CURRENT_TIMESTAMP`）

**不用在 Java 代码里手动 set**——MyBatis-Plus 的 `MetaObjectHandler` 自动填充，或数据库 DEFAULT 自动填充，两者选一。

**本项目用的是 MP 自动填充**（`@TableField(fill = FieldFill.INSERT)` + `MetaObjectHandler`）。

## 9.2 为什么用 DATETIME 而不是 TIMESTAMP

| 类型 | 范围 | 时区处理 | 存储 |
|---|---|---|---|
| `DATETIME` | 1000-01-01 到 9999-12-31 | 不存时区信息，按字面值存 | 8 字节 |
| `TIMESTAMP` | 1970-01-01 到 2038-01-19 | 存 UTC 时间，查询时按会话时区转换 | 4 字节 |

**本项目选 DATETIME**，原因：
1. 范围更大（不受 2038 问题限制）
2. 不涉及时区转换，查询结果直观
3. 若依也用 DATETIME

## 9.3 自动填充 vs 数据库 DEFAULT

| 方式 | 实现 | 优点 | 缺点 |
|---|---|---|---|
| 数据库 DEFAULT | `DEFAULT CURRENT_TIMESTAMP` | 简单，不依赖应用层 | 如果用 MP 插入时不包含时间字段，数据库 DEFAULT 会生效；但 MP 的 `MetaObjectHandler` 会在应用层先填充，数据库 DEFAULT 就被覆盖了 |
| MP MetaObjectHandler | `@TableField(fill=INSERT)` | 统一在 Java 层处理，方便加业务逻辑（比如填充"操作人 ID"） | 依赖 MP 框架 |

**本项目两层都配了**——数据库 DEFAULT 作为兜底（万一有人直接用 SQL 插入数据也能有时间），MP MetaObjectHandler 作为主要方式。

---

# 第十章：RBAC 在系统各阶段的体现

## 10.1 阶段 1-2：数据层就绪

RBAC 的五张表 DDL 已设计并执行，实体/Mapper/CRUD 已实现。此时权限模型只是"数据结构"，还没有被使用。

## 10.2 阶段 3：菜单树与动态菜单

RBAC 开始发挥作用：

- `GET /menu/tree`（全量菜单树）→ 管理员分配权限时的勾选树
- `GET /menu/user-tree?userId=`（动态菜单）→ 根据用户角色返回不同菜单

**权限流转链路：** `userId → sys_user_role → sys_role_menu → sys_menu → buildTree`

## 10.3 阶段 4：认证授权

RBAC 完整闭环：

```
登录 → 查用户 → 查角色 → 查权限(perms) → 生成 JWT（Payload 含权限列表）
请求 → JWT 过滤器 → 解析权限 → @PreAuthorize 检查
```

**这是 RBAC 模型的核心价值所在：** 用户→角色→权限的链路在这里被完整使用。

## 10.4 阶段 5：缓存加速

RBAC 的查询链路（userId→roleIds→menuIds→perms）可以被缓存。用户的权限列表变动频率低，适合用 Redis 缓存，减少数据库查询。

---

# 第十一章：RBAC 扩展方向

## 11.1 数据权限（行级权限）

当前的 RBAC 只控制"能不能访问某个接口"（功能权限）。数据权限控制"能看到哪些数据行"。

```
功能权限：张三能不能看到"用户管理"页面？（能）
数据权限：张三在"用户管理"页面里能看到哪些用户？（只看他部门的 / 只看自己的）
```

**实现方式：** 在 sys_role 里加 `data_scope` 字段：

| data_scope 值 | 含义 |
|---|---|
| `1` | 全部数据权限 |
| `2` | 自定义数据权限（通过 sys_role_dept 关联表指定） |
| `3` | 本部门数据权限 |
| `4` | 本部门及以下数据权限 |
| `5` | 仅本人数据权限 |

SQL 查询时自动追加数据权限条件：

```sql
-- data_scope=5（仅本人）时，用户列表查询自动追加：
SELECT * FROM sys_user WHERE del_flag=0 AND create_by = #{currentUserId}
```

**若依的 ruoyi-vue-pro 就实现了完整的数据权限。**

## 11.2 多租户

多租户（Multi-Tenancy）是 SaaS 系统的核心——一个系统服务多个客户（租户），每个租户的数据互相隔离。

RBAC 模型需要扩展：

```
租户 → 租户下的角色 → 租户下的用户/菜单
```

所有核心表加 `tenant_id` 字段，查询时自动追加租户过滤。

## 11.3 动态权限（运行时权限）

有些权限不是静态的，而是运行时决定的：

```
"只能编辑自己创建的用户" → 这个权限取决于"当前用户"和"目标用户"的关系
"只能审批金额 < 10000 的报销" → 取决于报销金额
```

这种权限无法用 `sys_menu.perms` 静态配置，需要在业务代码里动态判断。

---

## 附录：五张表的完整关系矩阵

| 操作 | sys_user | sys_role | sys_menu | sys_user_role | sys_role_menu |
|---|---|---|---|---|---|
| 新增用户 | `INSERT` | - | - | `INSERT`（分配角色） | - |
| 删除用户 | `UPDATE del_flag=1` | - | - | `DELETE`（清理关联） | - |
| 新增角色 | - | `INSERT` | - | - | `INSERT`（分配菜单） |
| 删除角色 | - | `UPDATE del_flag=1` | - | `DELETE`（清理关联） | `DELETE`（清理关联） |
| 分配角色给用户 | - | - | - | `INSERT` | - |
| 取消用户的角色 | - | - | - | `DELETE` | - |
| 分配菜单给角色 | - | - | - | - | `INSERT` |
| 查用户的权限 | `SELECT id` | - | `SELECT perms` | `SELECT role_id` | `SELECT menu_id` |
| 查角色下的用户 | `SELECT id` | `SELECT id` | - | `SELECT user_id` | - |
