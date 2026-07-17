package cheer.vo;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 菜单树视图对象（用于前端渲染菜单）
 * <p>
 * 与 Menu 实体的区别：增加了 children 字段用于树形嵌套
 */
@Data
public class MenuTreeVO {
    private Long id;
    /** 父菜单ID */
    private Long parentId;
    /** 菜单名称 */
    private String menuName;
    /** 类型：M=目录  C=菜单  F=按钮 */
    private String menuType;
    /** 前端路由地址 */
    private String path;
    /** 前端组件路径 */
    private String component;
    /** 权限标识 */
    private String perms;
    /** 菜单图标 */
    private String icon;
    /** 显示顺序 */
    private Integer sort;
    /** 状态 */
    private Integer status;
    /** 子菜单列表 */
    private List<MenuTreeVO> children = new ArrayList<>();
}
