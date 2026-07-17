package cheer.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 角色实体（对应 sys_role 表）
 */
@Data
@TableName("sys_role")
public class Role {
    /** 角色ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 角色名称 */
    private String roleName;
    /** 角色权限标识（如 admin、common） */
    private String roleKey;
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
