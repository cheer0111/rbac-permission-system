package cheer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 菜单/权限实体（对应 sys_menu 表，支持树形结构）
 * <p>
 * menuType：M=目录  C=菜单  F=按钮（权限）
 */
@Data
@TableName("sys_menu")
public class Menu {
    /** 菜单ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 父菜单ID，0 表示顶级 */
    private Long parentId;
    /** 菜单/按钮名称 */
    private String menuName;
    /** 类型：M=目录  C=菜单  F=按钮 */
    private String menuType;
    /** 前端路由地址 */
    private String path;
    /** 前端组件路径 */
    private String component;
    /** 权限标识（如 system:user:add），按钮类型必填 */
    private String perms;
    /** 菜单图标 */
    private String icon;
    /** 显示顺序 */
    private Integer sort;
    /** 状态：1=启用 0=禁用 */
    private Integer status;
    /** 创建时间（自动填充） */
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    /** 更新时间（自动填充） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
    /** 逻辑删除：0=正常 1=已删除 */
    @TableLogic
    private Integer delFlag;
}
