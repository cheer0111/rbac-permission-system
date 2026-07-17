package cheer.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 用户-角色关联实体（对应 sys_user_role 表，联合主键）
 */
@Data
@TableName("sys_user_role")
public class UserRole {
    /** 用户ID */
    private Long userId;
    /** 角色ID */
    private Long roleId;
}
