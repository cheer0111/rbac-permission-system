package cheer.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 登录请求参数
 */
@Data
public class LoginDTO {
    /** 登录账号 */
    @NotBlank
    private String username;
    /** 密码 */
    @NotBlank
    private String password;
}
