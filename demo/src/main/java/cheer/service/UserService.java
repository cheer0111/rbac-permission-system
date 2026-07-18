package cheer.service;

import cheer.dto.UserDTO;
import cheer.entity.User;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户服务接口
 */
public interface UserService {

    /**
     * 新增用户（含用户名查重 + BCrypt 密码加密）
     */
    User add(UserDTO userDTO);

    /**
     * 用户分页条件查询
     */
    Page<User> query(int current, int size, String username, Integer status);

    /**
     * 逻辑删除用户
     */
    void delete(Long id);

    /**
     * 更新用户头像
     */
    void upload(String avatar, Long id);
}
