package cheer.entity;

import com.baomidou.mybatisplus.annotation.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体（对应 sys_user 表）
 */
@Data
@TableName("sys_user")
public class User {
    /** 用户ID（雪花算法） */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 登录账号 */
    @NotBlank
    private String username;
    /** 密码（BCrypt 加密存储） */
    @NotBlank
    private String password;
    /** 用户昵称 */
    @NotBlank
    private String nickname;
    /** 头像URL */
    private String avatar;
    /** 邮箱 */
    private String email;
    /** 手机号 */
    private String phone;
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
