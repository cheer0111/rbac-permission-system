package cheer.service;

import cheer.entity.Menu;
import cheer.vo.MenuTreeVO;

import java.util.List;

/**
 * 菜单服务接口（含菜单树构建 + Redis 缓存）
 */
public interface MenuService {

    /**
     * 将扁平菜单列表构建为树形结构
     *
     * @param menus 扁平菜单列表
     * @return 树形菜单列表（只包含顶级节点，子节点通过 children 嵌套）
     */
    List<MenuTreeVO> buildTree(List<Menu> menus);

    /**
     * 获取全量菜单树（缓存优先）
     */
    List<MenuTreeVO> tree();

    /**
     * 获取指定用户的动态菜单树（根据角色过滤，缓存优先）
     *
     * @param userId 用户ID
     */
    List<MenuTreeVO> userTree(Long userId);

    /**
     * 获取指定用户的所有权限标识列表（缓存优先）
     *
     * @param userId 用户ID
     * @return 权限标识列表，如 ["system:user:add", "system:user:query"]
     */
    List<String> getPermissionsByUserId(Long userId);
}
