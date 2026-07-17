package cheer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 角色-菜单关联实体（对应 sys_role_menu 表，联合主键）
 */
@Data
@TableName("sys_role_menu")
public class RoleMenu {
    /** 角色ID */
    private Long roleId;
    /** 菜单ID */
    private Long menuId;
}
