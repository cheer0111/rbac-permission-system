# RBAC 权限模型代码实现指南

> 本文档是 `rbac-model-design-report.md` 的配套实现篇。
> 涵盖五张表的完整 CRUD 操作、中间表的读写操作、以及权限查询链路的代码实现。
> 所有代码基于 `cheer.demo` 项目的实际结构和约定编写。

---

## 目录

- [第一章：准备工作——实体与 Mapper 回顾](#第一章准备工作实体与-mapper-回顾)
  - [1.1 当前已有的实体和 Mapper](#11-当前已有的实体和-mapper)
  - [1.2 需要新增的 DTO](#12-需要新增的-dto)
- [第二章：角色管理 CRUD](#第二章角色管理-crud)
  - [2.1 新增角色](#21-新增角色)
  - [2.2 查询角色列表（分页）](#22-查询角色列表分页)
  - [2.3 查询角色详情](#23-查询角色详情)
  - [2.4 修改角色](#24-修改角色)
  - [2.5 删除角色](#25-删除角色)
  - [2.6 角色状态切换（启用/禁用）](#26-角色状态切换启用禁用)
- [第三章：角色-权限关联操作](#第三章角色-权限关联操作)
  - [3.1 查询角色已分配的权限列表](#31-查询角色已分配的权限列表)
  - [3.2 给角色分配权限（全量覆盖）](#32-给角色分配权限全量覆盖)
  - [3.3 两种分配策略对比：增量 vs 全量覆盖](#33-两种分配策略对比增量-vs-全量覆盖)
- [第四章：用户-角色关联操作](#第四章用户-角色关联操作)
  - [4.1 查询用户已拥有的角色列表](#41-查询用户已拥有的角色列表)
  - [4.2 给用户分配角色（全量覆盖）](#42-给用户分配角色全量覆盖)
  - [4.3 从 Service 层完整走一遍"查用户权限"链路](#43-从-service-层完整走一遍查用户权限链路)
- [第五章：权限查询链路的完整实现](#第五章权限查询链路的完整实现)
  - [5.1 链路图](#51-链路图)
  - [5.2 完整实现代码](#52-完整实现代码)
  - [5.3 用 Postman 验证整条链路](#53-用-postman-验证整条链路)
- [第六章：中间表操作的注意事项](#第六章中间表操作的注意事项)
  - [6.1 MyBatis-Plus 对联合主键的处理](#61-mybatis-plus-对联合主键的处理)
  - [6.2 物理删除中间表记录的正确方式](#62-物理删除中间表记录的正确方式)
  - [6.3 批量插入中间表记录](#63-批量插入中间表记录)

---

# 第一章：准备工作——实体与 Mapper 回顾

## 1.1 当前已有的实体和 Mapper

你已经创建了以下实体和 Mapper：

| 实体 | Mapper | 说明 |
|---|---|---|
| `User` | `UserMapper` | ✅ 已有，有完整的 CRUD |
| `Role` | `RoleMapper` | ✅ 已有，Mapper 为空（只继承 BaseMapper） |
| `Menu` | `MenuMapper` | ✅ 已有，有 buildTree 和查询逻辑 |
| `UserRole` | `UserRoleMapper` | ✅ 已有，Mapper 为空 |
| `RoleMenu` | `RoleMenuMapper` | ✅ 已有，Mapper 为空 |

**Role / UserRole / RoleMenu 的 Mapper 目前只继承了 `BaseMapper<T>`，没有自定义方法。** 中间表的基本操作用 MP 的 Wrapper 就够了。

## 1.2 需要新增的 DTO

角色管理需要接收前端参数的 DTO：

```
cheer.demo.dto.RoleDTO.java      — 新增/修改角色时接收参数
cheer.demo.dto.AssignRoleDTO.java — 给用户分配角色时接收参数
cheer.demo.dto.AssignMenuDTO.java — 给角色分配权限时接收参数
```

---

# 第二章：角色管理 CRUD

## 2.1 新增角色

### Service 接口

```java
// RoleService.java 新增方法
Role add(RoleDTO roleDTO);
```

### Service 实现

```java
// RoleServiceImpl.java
@Override
public Role add(RoleDTO roleDTO) {
    // 1. roleKey 唯一性校验
    LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Role::getRoleKey, roleDTO.getRoleKey());
    Long count = roleMapper.selectCount(wrapper);
    if (count != null && count > 0) {
        throw new BusinessException(ResultCode.PARAM_ERROR, "角色标识已存在");
    }

    // 2. DTO → Entity
    Role role = new Role();
    BeanUtils.copyProperties(roleDTO, role);

    // 3. 插入（createTime/updateTime 由 MetaObjectHandler 自动填充）
    roleMapper.insert(role);
    return role;
}
```

### Controller

```java
// RoleController.java
@PostMapping("/add")
public Result<Role> add(@Valid RoleDTO roleDTO) {
    Role role = roleService.add(roleDTO);
    return Result.success(role, ResultCode.SUCCESS, "新增成功");
}
```

### RoleDTO

```java
@Data
public class RoleDTO {
    @NotBlank(message = "角色名称不能为空")
    private String roleName;

    @NotBlank(message = "角色标识不能为空")
    private String roleKey;

    private Integer sort;
}
```

### 设计要点

1. **roleKey 必须唯一**——它是代码里判断角色的标识符（如 `admin`、`common`），重复会导致权限判断混乱
2. **roleName 不需要唯一**——"普通用户"和"普通员工"可以同时存在，因为 roleKey 不同
3. **status 默认 1（启用）**——新角色默认启用，不需要前端传
4. **createTime/updateTime 不手动 set**——MetaObjectHandler 自动填充

## 2.2 查询角色列表（分页）

### Service

```java
// RoleServiceImpl.java
public Page<Role> queryPage(int current, int size, String roleName, String roleKey, Integer status) {
    Page<Role> page = new Page<>(current, size);
    LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
    if (StringUtils.isNotBlank(roleName)) {
        wrapper.like(Role::getRoleName, roleName);
    }
    if (StringUtils.isNotBlank(roleKey)) {
        wrapper.like(Role::getRoleKey, roleKey);
    }
    if (status != null) {
        wrapper.eq(Role::getStatus, status);
    }
    wrapper.orderByAsc(Role::getSort);
    return roleMapper.selectPage(page, wrapper);
}
```

### Controller

```java
@GetMapping("/page")
public Result<Page<Role>> page(int current, int size,
                              @RequestParam(required = false) String roleName,
                              @RequestParam(required = false) String roleKey,
                              @RequestParam(required = false) Integer status) {
    Page<Role> page = roleService.queryPage(current, size, roleName, roleKey, status);
    return Result.success(page, ResultCode.SUCCESS, "查询成功");
}
```

### 设计要点

1. **分页 + 条件查询**——和你之前的用户列表查询同一个模式
2. **`@RequestParam(required = false)`**——参数可选，不传时为 null，Wrapper 不拼条件
3. **按 sort 排序**——角色在管理界面按 sort 字段排序显示

## 2.3 查询角色详情

```java
// RoleServiceImpl.java
public Role getById(Long id) {
    Role role = roleMapper.selectById(id);
    if (role == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
    }
    return role;
}
```

**设计要点：** 用 `selectById` 就行——`@TableLogic` 自动过滤已删除的角色。如果查出来是 null，说明角色不存在或已被删除。

## 2.4 修改角色

```java
// RoleServiceImpl.java
public void update(RoleDTO roleDTO) {
    // 1. 角色必须存在
    Role existing = roleMapper.selectById(roleDTO.getId());
    if (existing == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
    }

    // 2. 如果修改了 roleKey，检查唯一性
    if (StringUtils.isNotBlank(roleDTO.getRoleKey())
            && !roleDTO.getRoleKey().equals(existing.getRoleKey())) {
        LambdaQueryWrapper<Role> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Role::getRoleKey, roleDTO.getRoleKey());
        Long count = roleMapper.selectCount(wrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "角色标识已存在");
        }
    }

    // 3. 更新
    Role role = new Role();
    BeanUtils.copyProperties(roleDTO, role);
    roleMapper.updateById(role);
}
```

**设计要点：** 修改 roleKey 时要做唯一性校验——但要先排除自身（`existing.getRoleKey()`）。

## 2.5 删除角色

```java
// RoleServiceImpl.java
public void delete(Long id) {
    Role existing = roleMapper.selectById(id);
    if (existing == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
    }

    // 1. 检查是否有用户关联此角色
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getRoleId, id);
    Long userCount = userRoleMapper.selectCount(wrapper);
    if (userCount != null && userCount > 0) {
        throw new BusinessException(ResultCode.PARAM_ERROR, "该角色已分配给用户，无法删除");
    }

    // 2. 逻辑删除角色
    roleMapper.deleteById(id);

    // 3. 物理删除角色-菜单关联（中间表可以物理删除）
    LambdaQueryWrapper<RoleMenu> rmWrapper = new LambdaQueryWrapper<>();
    rmWrapper.eq(RoleMenu::getRoleId, id);
    roleMenuMapper.delete(rmWrapper);
}
```

**设计要点：**

1. **删除前检查关联**——如果有用户还在用这个角色，不能删除（否则这些用户的权限会"消失"但不报错，很难排查）
2. **核心表逻辑删除 + 中间表物理删除**——角色表走 `deleteById`（`@TableLogic` 自动转 UPDATE），中间表直接物理 DELETE
3. **顺序很重要**——先删中间表关联，再删角色本身（反过来也没问题，因为逻辑删除不影响中间表查询）

## 2.6 角色状态切换（启用/禁用）

```java
// RoleServiceImpl.java
public void toggleStatus(Long id) {
    Role role = roleMapper.selectById(id);
    if (role == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
    }
    role.setStatus(role.getStatus() == 1 ? 0 : 1);
    roleMapper.updateById(role);
}
```

**设计要点：** 禁用角色后，拥有该角色的用户不会自动"失去"这个角色（中间表记录还在），但登录时可以从 `sys_role` 的 `status` 字段过滤掉已禁用的角色。

---

# 第三章：角色-权限关联操作

## 3.1 查询角色已分配的权限列表

这是"角色管理 → 分配权限"页面的数据源——打开某个角色时，需要知道它当前拥有哪些菜单权限（用于勾选树的回显）。

### Service

```java
// RoleServiceImpl.java
public List<Long> getMenuIdsByRoleId(Long roleId) {
    LambdaQueryWrapper<RoleMenu> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(RoleMenu::getRoleId, roleId);
    return roleMenuMapper.selectList(wrapper)
            .stream()
            .map(RoleMenu::getMenuId)
            .collect(Collectors.toList());
}
```

### Controller

```java
@GetMapping("/{roleId}/menuIds")
public Result<List<Long>> getMenuIds(@PathVariable Long roleId) {
    List<Long> menuIds = roleService.getMenuIdsByRoleId(roleId);
    return Result.success(menuIds, ResultCode.SUCCESS, "查询成功");
}
```

**前端用法：** 拿到 `menuIds` 列表后，在勾选树上把对应的菜单设为"已勾选"状态。

## 3.2 给角色分配权限（全量覆盖）

"管理员在勾选树上勾选了一些菜单，点击保存"——这就是"给角色分配权限"。

### 思路

**全量覆盖**：不管之前角色有什么权限，全部删掉，重新写入新选的权限。

```
旧权限 [2001, 2002, 2003]  →  DELETE 全删
新权限 [2001, 2002, 2004]  →  INSERT 全写
```

### Service

```java
// RoleServiceImpl.java
@Transactional
public void assignMenus(Long roleId, List<Long> menuIds) {
    // 1. 角色必须存在
    Role role = roleMapper.selectById(roleId);
    if (role == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "角色不存在");
    }

    // 2. 删除该角色的所有旧权限（中间表物理删除）
    LambdaQueryWrapper<RoleMenu> deleteWrapper = new LambdaQueryWrapper<>();
    deleteWrapper.eq(RoleMenu::getRoleId, roleId);
    roleMenuMapper.delete(deleteWrapper);

    // 3. 插入新权限（menuIds 不为空时才插入）
    if (menuIds != null && !menuIds.isEmpty()) {
        for (Long menuId : menuIds) {
            RoleMenu roleMenu = new RoleMenu();
            roleMenu.setRoleId(roleId);
            roleMenu.setMenuId(menuId);
            roleMenuMapper.insert(roleMenu);
        }
    }
}
```

### Controller

```java
@PostMapping("/{roleId}/menus")
public Result assignMenus(@PathVariable Long roleId, @RequestBody List<Long> menuIds) {
    roleService.assignMenus(roleId, menuIds);
    return Result.success(null, ResultCode.SUCCESS, "分配成功");
}
```

### AssignMenuDTO（可选）

```java
@Data
public class AssignMenuDTO {
    @NotNull(message = "角色ID不能为空")
    private Long roleId;

    @NotEmpty(message = "权限列表不能为空")
    private List<Long> menuIds;
}
```

### 设计要点

1. **@Transactional**——删除旧权限 + 插入新权限必须在同一个事务里，否则删除成功但插入失败 → 角色权限为空
2. **全量覆盖 vs 增量更新**——全量覆盖更简单、不容易出 bug。增量更新（"只加新的、不删旧的"）需要对比新旧列表算差集，复杂且容易遗漏
3. **menuIds 为空 = 取消所有权限**——管理员把所有勾选取消，角色就没有任何权限了。这种场景用全量覆盖天然支持

## 3.3 两种分配策略对比：增量 vs 全量覆盖

| | 全量覆盖（本项目采用） | 增量更新 |
|---|---|---|
| 做法 | 先删后写 | 只写新增的，只删取消的 |
| SQL | 1 次 DELETE + N 次 INSERT | N 次 INSERT + N 次 DELETE |
| 代码复杂度 | 低（不需要对比新旧列表） | 高（需要算差集） |
| 出错风险 | 低 | 高（容易忘记删旧的） |
| 适用场景 | 大多数情况 | 需要审计"谁在什么时间加/减了什么权限"时 |

---

# 第四章：用户-角色关联操作

## 4.1 查询用户已拥有的角色列表

```java
// UserServiceImpl.java
public List<Role> getRolesByUserId(Long userId) {
    // 1. 从中间表查出 roleId 列表
    LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserRole::getUserId, userId);
    List<Long> roleIds = userRoleMapper.selectList(wrapper)
            .stream()
            .map(UserRole::getRoleId)
            .collect(Collectors.toList());

    if (roleIds.isEmpty()) {
        return Collections.emptyList();
    }

    // 2. 根据 roleId 列表查角色详情
    LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
    roleWrapper.in(Role::getId, roleIds)
                .eq(Role::getStatus, 1)       // 只查启用的角色
                .orderByAsc(Role::getSort);
    return roleMapper.selectList(roleWrapper);
}
```

### Controller

```java
@GetMapping("/{userId}/roles")
public Result<List<Role>> getRoles(@PathVariable Long userId) {
    List<Role> roles = userService.getRolesByUserId(userId);
    return Result.success(roles, ResultCode.SUCCESS, "查询成功");
}
```

**设计要点：** 查角色时加了 `status=1` 过滤——已禁用的角色不返回给前端（虽然中间表记录还在，但禁用的角色不应该被用户使用）。

## 4.2 给用户分配角色（全量覆盖）

和"给角色分配权限"同样的模式——先删后写。

```java
// UserServiceImpl.java
@Transactional
public void assignRoles(Long userId, List<Long> roleIds) {
    // 1. 用户必须存在
    User user = userMapper.selectById(userId);
    if (user == null) {
        throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
    }

    // 2. 校验所有 roleId 都存在且已启用
    if (roleIds != null && !roleIds.isEmpty()) {
        LambdaQueryWrapper<Role> roleWrapper = new LambdaQueryWrapper<>();
        roleWrapper.in(Role::getId, roleIds).eq(Role::getStatus, 1);
        Long validCount = roleMapper.selectCount(roleWrapper);
        if (validCount == null || validCount != roleIds.size()) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "存在无效或已禁用的角色");
        }
    }

    // 3. 删除该用户的所有旧角色
    LambdaQueryWrapper<UserRole> deleteWrapper = new LambdaQueryWrapper<>();
    deleteWrapper.eq(UserRole::getUserId, userId);
    userRoleMapper.delete(deleteWrapper);

    // 4. 插入新角色
    if (roleIds != null && !roleIds.isEmpty()) {
        for (Long roleId : roleIds) {
            UserRole userRole = new UserRole();
            userRole.setUserId(userId);
            userRole.setRoleId(roleId);
            userRoleMapper.insert(userRole);
        }
    }
}
```

### Controller

```java
@PostMapping("/{userId}/roles")
public Result assignRoles(@PathVariable Long userId, @RequestBody List<Long> roleIds) {
    userService.assignRoles(userId, menuIds);
    return Result.success(null, ResultCode.SUCCESS, "分配成功");
}
```

**设计要点：**

1. **校验 roleId 的有效性**——防止前端传入不存在的角色 ID 或已禁用的角色 ID
2. **@Transactional**——和角色分配权限一样，先删后写必须在事务里
3. **全量覆盖**——用户的旧角色全部清空，重新写入新角色

## 4.3 从 Service 层完整走一遍"查用户权限"链路

这是 RBAC 模型的核心链路——给定一个 userId，查出他拥有的所有权限字符串。

```java
// UserServiceImpl.java
public List<String> getPermissionsByUserId(Long userId) {
    // 步骤 1：userId → roleIds
    LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
    urWrapper.eq(UserRole::getUserId, userId);
    List<Long> roleIds = userRoleMapper.selectList(urWrapper)
            .stream()
            .map(UserRole::getRoleId)
            .collect(Collectors.toList());

    if (roleIds.isEmpty()) {
        return Collections.emptyList();
    }

    // 步骤 2：roleIds → menuIds
    LambdaQueryWrapper<RoleMenu> rmWrapper = new LambdaQueryWrapper<>();
    rmWrapper.in(RoleMenu::getRoleId, roleIds);
    List<Long> menuIds = roleMenuMapper.selectList(rmWrapper)
            .stream()
            .map(RoleMenu::getMenuId)
            .collect(Collectors.toList());

    if (menuIds.isEmpty()) {
        return Collections.emptyList();
    }

    // 步骤 3：menuIds → perms 权限字符串列表
    LambdaQueryWrapper<Menu> menuWrapper = new LambdaQueryWrapper<>();
    menuWrapper.in(Menu::getId, menuIds)
                .isNotNull(Menu::getPerms)        // 只取有 perms 的菜单（按钮和部分菜单）
                .eq(Menu::getStatus, 1)             // 只取启用的菜单
                .select(Menu::getPerms);            // 只查 perms 列，不查全列
    List<Menu> menus = menuMapper.selectList(menuWrapper);

    return menus.stream()
            .map(Menu::getPerms)
            .collect(Collectors.toList());
}
```

**这段代码你在阶段 4（JWT 登录）时会直接用到**——登录成功后，调用这个方法拿到权限列表，塞进 JWT 的 Payload。

---

# 第五章：权限查询链路的完整实现

## 5.1 链路图

```
                  sys_user_role    sys_role_menu     sys_menu
userId ──────→ roleIds ──────→ menuIds ──────→ perms
  (1)            (2)              (3)            (4)
```

| 步骤 | 操作 | 输入 | 输出 | SQL |
|---|---|---|---|---|
| (1) | 查用户角色 | userId | roleIds | `SELECT role_id FROM sys_user_role WHERE user_id=?` |
| (2) | 查角色菜单 | roleIds | menuIds | `SELECT menu_id FROM sys_role_menu WHERE role_id IN (...)` |
| (3) | 查菜单权限 | menuIds | perms | `SELECT perms FROM sys_menu WHERE id IN (...) AND perms IS NOT NULL` |
| (4) | 组装结果 | perms | List\<String\> | 纯 Java 操作 |

## 5.2 完整实现代码

上面 4.3 节的 `getPermissionsByUserId` 就是完整实现。这里再给出一个**带注释的版本**，方便你逐行理解：

```java
/**
 * 查询指定用户拥有的所有权限字符串
 * 链路：userId → sys_user_role → roleIds → sys_role_menu → menuIds → sys_menu(perms)
 *
 * @param userId 用户ID
 * @return 权限字符串列表，如 ["system:user:list", "system:user:add", ...]
 */
public List<String> getPermissionsByUserId(Long userId) {
    // ===== 步骤 1：查该用户有哪些角色 =====
    // 从中间表 sys_user_role 中，按 user_id 过滤，拿到 role_id 列表
    LambdaQueryWrapper<UserRole> urWrapper = new LambdaQueryWrapper<>();
    urWrapper.eq(UserRole::getUserId, userId);
    List<Long> roleIds = userRoleMapper.selectList(urWrapper)
            .stream()                     // List<UserRole> → Stream
            .map(UserRole::getRoleId)      // 每个 UserRole → 提取 roleId
            .collect(Collectors.toList()); // Stream → List<Long>

    // 没有角色 → 没有权限 → 直接返回空列表
    if (roleIds.isEmpty()) {
        return Collections.emptyList();
    }

    // ===== 步骤 2：查这些角色关联了哪些菜单 =====
    // 从中间表 sys_role_menu 中，按 role_id IN (roleIds) 过滤，拿到 menu_id 列表
    LambdaQueryWrapper<RoleMenu> rmWrapper = new LambdaQueryWrapper<>();
    rmWrapper.in(RoleMenu::getRoleId, roleIds);  // IN 条件
    List<Long> menuIds = roleMenuMapper.selectList(rmWrapper)
            .stream()
            .map(RoleMenu::getMenuId)
            .collect(Collectors.toList());

    // 没有关联菜单 → 没有权限
    if (menuIds.isEmpty()) {
        return Collections.emptyList();
    }

    // ===== 步骤 3：查这些菜单的权限标识 =====
    // 从 sys_menu 中，按 id IN (menuIds) 过滤，只取有 perms 的、且启用的
    LambdaQueryWrapper<Menu> menuWrapper = new LambdaQueryWrapper<>();
    menuWrapper.in(Menu::getId, menuIds)        // IN 条件
                .isNotNull(Menu::getPerms)       // 只取有 perms 的（目录 M 通常没有）
                .eq(Menu::getStatus, 1)            // 只取启用的菜单
                .select(Menu::getPerms);          // 只查 perms 列（减少数据传输）
    List<Menu> menus = menuMapper.selectList(menuWrapper);

    // ===== 步骤 4：提取权限字符串 =====
    return menus.stream()
            .map(Menu::getPerms)               // 每个 Menu → 提取 perms 字段
            .collect(Collectors.toList());      // → List<String>
}
```

## 5.3 用 Postman 验证整条链路

**准备数据（如果你之前执行过样例数据就不用重复执行）：**

```sql
USE rbac_demo;

-- 用户 1（admin）关联角色 1001（admin）
INSERT INTO sys_user_role (user_id, role_id) VALUES (1, 1001);

-- 用户 2（common）关联角色 1002（common）
INSERT INTO sys_user_role (user_id, role_id) VALUES (2, 1002);

-- 角色 1001（admin）关联全部菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1001, 2001), (1001, 2002), (1001, 2003), (1001, 2004), (1001, 2005);

-- 角色 1002（common）关联部分菜单
INSERT INTO sys_role_menu (role_id, menu_id) VALUES
(1002, 2001), (1002, 2002), (1002, 2003);
```

**测试：**

假设你给 UserService 新增了一个 `GET /user/{userId}/permissions` 接口：

```
GET /user/1/permissions
预期返回：["system:user:list", "system:user:add", "system:role:list"]
（admin 角色有全部权限）

GET /user/2/permissions
预期返回：["system:user:list", "system:user:add"]
（common 角色只有用户管理相关的权限）
```

**如果返回为空：**
- 检查 sys_user_role 表里是否有该用户的记录
- 检查 sys_role_menu 表里是否有对应角色的记录
- 检查 sys_menu 表里 perms 字段是否为 NULL（目录 M 的 perms 通常是 NULL）

---

# 第六章：中间表操作的注意事项

## 6.1 MyBatis-Plus 对联合主键的处理

`sys_user_role` 和 `sys_role_menu` 的联合主键在实体里不加 `@TableId`。MP 会把它们当作**没有主键的表**来处理。

这意味着：

| 操作 | 行为 |
|---|---|
| `insert(userRole)` | 正常工作，生成 INSERT 语句（不会自动生成主键值） |
| `selectById(...)` | **不能用**——MP 不知道主键是哪个字段 |
| `updateById(...)` | **不能用**——同上 |
| `deleteById(...)` | **不能用**——同上 |
| `selectList(wrapper)` | ✅ 正常用 Wrapper 构造条件查询 |
| `delete(wrapper)` | ✅ 正常用 Wrapper 构造条件删除 |

**中间表永远不要用 `selectById` / `updateById` / `deleteById`**——用 Wrapper 代替。

## 6.2 物理删除中间表记录的正确方式

```java
// ❌ 错误：deleteById 不工作（联合主键没有 @TableId）
userRoleMapper.deleteById(someId);

// ✅ 正确：用 Wrapper 构造条件
LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(UserRole::getUserId, userId)
       .eq(UserRole::getRoleId, roleId);    // 精确定位一条记录
userRoleMapper.delete(wrapper);

// ✅ 也正确：删除某用户的所有角色
LambdaQueryWrapper<UserRole> wrapper = new LambdaQueryWrapper<>();
wrapper.eq(UserRole::getUserId, userId);
userRoleMapper.delete(wrapper);
```

## 6.3 批量插入中间表记录

MP 的 `BaseMapper.insert()` 一次只能插一条。如果需要批量插入，有两种方式：

### 方式一：循环 insert（简单，本项目采用）

```java
for (Long roleId : roleIds) {
    UserRole userRole = new UserRole();
    userRole.setUserId(userId);
    userRole.setRoleId(roleId);
    userRoleMapper.insert(userRole);
}
```

**特点：** 每条记录一次 INSERT 语句。分配 10 个角色 = 10 次 INSERT。

### 方式二：MyBatis XML 批量插入（性能更好）

```xml
<!-- UserRoleMapper.xml -->
<insert id="batchInsert">
    INSERT INTO sys_user_role (user_id, role_id) VALUES
    <foreach collection="list" item="item" separator=",">
        (#{item.userId}, #{item.roleId})
    </foreach>
</insert>
```

```java
// UserRoleMapper.java
void batchInsert(@Param("list") List<UserRole> list);
```

**特点：** 一次 INSERT 语句插入所有记录。分配 10 个角色 = 1 次 INSERT。

### 什么时候需要优化？

| 数据量 | 方式 | 原因 |
|---|---|---|
| < 50 条 | 循环 insert 足够 | 50 次 INSERT 毫秒级，不值得优化 |
| 50-1000 条 | 批量插入 XML | 减少数据库交互次数 |
| > 1000 条 | 批量插入 + 分批（每批 500） | 单条 SQL 太长也会慢 |

**本项目角色分配/权限分配通常不超过 20-30 条**，循环 insert 完全够用。

---

## 附录：完整的接口清单

### 用户管理（已有）

| 接口 | 方法 | 说明 |
|---|---|---|
| `POST /user/add` | UserController | 新增用户 |
| `GET /user/page` | UserController | 用户分页列表 |
| `DELETE /user/delete/{id}` | UserController | 删除用户（逻辑删除） |

### 角色管理（待实现）

| 接口 | 方法 | 说明 |
|---|---|---|
| `POST /role/add` | RoleController.add | 新增角色 |
| `GET /role/page` | RoleController.page | 角色分页列表 |
| `GET /role/{id}` | RoleController.getById | 角色详情 |
| `PUT /role` | RoleController.update | 修改角色 |
| `DELETE /role/{id}` | RoleController.delete | 删除角色（逻辑删除+清理关联） |
| `PUT /role/{id}/status` | RoleController.toggleStatus | 切换角色状态 |

### 角色-权限关联（待实现）

| 接口 | 方法 | 说明 |
|---|---|---|
| `GET /role/{roleId}/menuIds` | RoleController.getMenuIds | 查角色已分配的权限 ID 列表 |
| `POST /role/{roleId}/menus` | RoleController.assignMenus | 给角色分配权限（全量覆盖） |

### 用户-角色关联（待实现）

| 接口 | 方法 | 说明 |
|---|---|---|
| `GET /user/{userId}/roles` | UserController.getRoles | 查用户已拥有的角色列表 |
| `POST /user/{userId}/roles` | UserController.assignRoles | 给用户分配角色（全量覆盖） |

### 菜单管理（已有）

| 接口 | 方法 | 说明 |
|---|---|---|
| `GET /menu/tree` | MenuController.tree | 全量菜单树 |
| `GET /menu/user-tree?userId=` | MenuController.userTree | 用户动态菜单树 |

### 权限查询（待实现）

| 接口 | 方法 | 说明 |
|---|---|---|
| `GET /user/{userId}/permissions` | UserController.getPermissions | 查用户权限字符串列表（阶段 4 JWT 登录用） |
