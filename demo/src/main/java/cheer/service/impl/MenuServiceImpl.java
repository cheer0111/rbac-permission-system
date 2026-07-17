package cheer.service.impl;

import cheer.entity.Menu;
import cheer.entity.RoleMenu;
import cheer.entity.UserRole;
import cheer.mapper.MenuMapper;
import cheer.mapper.RoleMenuMapper;
import cheer.mapper.UserRoleMapper;
import cheer.service.MenuService;
import cheer.vo.MenuTreeVO;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 菜单服务实现
 * <p>
 * 核心功能：
 * 1. 菜单树构建（扁平列表 → 树形结构，递归填充 children）
 * 2. 动态菜单查询（userId → roleIds → menuIds → 菜单列表 → 树）
 * 3. Redis 缓存（读穿透 + 降级，缓存 30 分钟）
 */
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

    /** 缓存 key 前缀 */
    private static final String USER_PERMS_KEY_PREFIX = "user:perms:";
    private static final String USER_MENU_TREE_KEY_PREFIX = "user:menuTree:";
    private static final String MENU_TREE_KEY_PREFIX = "menu:tree:";
    /** 缓存过期时间 */
    private static final long CACHE_TTL_MINUTES = 30;

    @Override
    public List<MenuTreeVO> buildTree(List<Menu> menus) {
        List<MenuTreeVO> vos = menus.stream()
                .map(this::toVO)
                .collect(Collectors.toList());
        List<MenuTreeVO> treeVOS = new ArrayList<>();
        for (MenuTreeVO vo : vos) {
            // parentId == 0 表示顶级节点
            if (vo.getParentId() != null && vo.getParentId().equals(0L)) {
                fillChildren(vo, vos);
                treeVOS.add(vo);
            }
        }
        return treeVOS;
    }

    /**
     * 获取全量菜单树（缓存优先，读穿透 + 降级查库）
     */
    @Override
    public List<MenuTreeVO> tree() {
        String key = MENU_TREE_KEY_PREFIX + "all";
        // 读缓存
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (json != null) {
                List<Menu> menus = objectMapper.readValue(json, new TypeReference<List<Menu>>() {});
                return buildTree(menus);
            }
        } catch (Exception e) {
            log.warn("读取菜单树缓存失败，降级查库: {}", e.getMessage());
        }
        // 查库 + 写缓存
        List<Menu> menu = menuMapper.selectList(null);
        try {
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(menu), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入菜单树缓存失败: {}", e.getMessage());
        }
        return buildTree(menu);
    }

    /**
     * 实体转 VO
     */
    private MenuTreeVO toVO(Menu menu) {
        MenuTreeVO vo = new MenuTreeVO();
        BeanUtils.copyProperties(menu, vo);
        return vo;
    }

    /**
     * 递归填充子节点：遍历所有 VO，将 parentId 匹配的挂到父节点的 children 下
     */
    private void fillChildren(MenuTreeVO parent, List<MenuTreeVO> allVOs) {
        for (MenuTreeVO vo : allVOs) {
            if (Objects.equals(vo.getParentId(), parent.getId())) {
                parent.getChildren().add(vo);
                fillChildren(vo, allVOs);
            }
        }
    }

    /**
     * 根据用户ID查询其角色ID列表
     */
    private List<Long> getRoleIdsByUserId(Long userId) {
        LambdaQueryWrapper<UserRole> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(UserRole::getUserId, userId);
        return userRoleMapper.selectList(queryWrapper)
                .stream()
                .map(UserRole::getRoleId)
                .collect(Collectors.toList());
    }

    /**
     * 根据角色ID列表查询其关联的菜单ID列表
     */
    private List<Long> getMenuIdsByRoleIdList(List<Long> roleIds) {
        LambdaQueryWrapper<RoleMenu> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(RoleMenu::getRoleId, roleIds);
        return roleMenuMapper.selectList(queryWrapper)
                .stream()
                .map(RoleMenu::getMenuId)
                .collect(Collectors.toList());
    }

    /**
     * 链路查询：userId → roleIds → menuIds → 菜单列表
     * 任意一步为空直接返回空列表，避免无效 SQL
     */
    private List<Menu> getMenusByUserId(Long userId) {
        List<Long> roleIds = getRoleIdsByUserId(userId);
        if (roleIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> menuIds = getMenuIdsByRoleIdList(roleIds);
        if (menuIds.isEmpty()) {
            return Collections.emptyList();
        }

        LambdaQueryWrapper<Menu> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Menu::getId, menuIds);
        return menuMapper.selectList(wrapper);
    }

    /**
     * 获取指定用户的动态菜单树（缓存优先）
     */
    @Override
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
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(tree), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入用户菜单树缓存失败: {}", e.getMessage());
        }
        return tree;
    }

    /**
     * 获取指定用户的所有权限标识（缓存优先）
     * 从菜单中提取 perms 字段，过滤空值，去重
     */
    @Override
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
            stringRedisTemplate.opsForValue().set(key, objectMapper.writeValueAsString(perms), CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.warn("写入用户权限缓存失败: {}", e.getMessage());
        }
        return perms;
    }

    /**
     * 清除指定用户的权限缓存和菜单树缓存（角色/菜单变更后调用）
     */
    public void evictUserPermsCache(Long userId) {
        stringRedisTemplate.delete(USER_PERMS_KEY_PREFIX + userId);
        stringRedisTemplate.delete(USER_MENU_TREE_KEY_PREFIX + userId);
    }
}
