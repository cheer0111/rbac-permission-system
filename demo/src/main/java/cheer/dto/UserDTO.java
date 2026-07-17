package cheer.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 新增用户请求参数
 */
@Data
public class UserDTO {
    /** 登录账号 */
    @NotBlank
    private String username;
    /** 密码（明文，Service 层会 BCrypt 加密） */
    @NotBlank
    private String password;
    /** 用户昵称 */
    @NotBlank
    private String nickname;
    /** 邮箱 */
    @Email
    private String email;
    /** 手机号 */
    private String phone;
}
