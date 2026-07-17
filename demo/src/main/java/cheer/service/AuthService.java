package cheer.service;

import cheer.dto.LoginDTO;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param dto 登录请求参数（用户名、密码）
     * @return JWT token
     */
    String login(LoginDTO dto);
}
