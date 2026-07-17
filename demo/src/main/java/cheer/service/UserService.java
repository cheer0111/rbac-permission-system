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
     *
     * @param userDTO 用户信息
     * @return 入库后的用户对象（含雪花ID）
     */
    User add(UserDTO userDTO);

    /**
     * 用户分页条件查询
     *
     * @param current  当前页码
     * @param size     每页条数
     * @param username 用户名（模糊，可选）
     * @param status   状态（精确，可选）
     * @return 分页结果
     */
    Page<User> query(int current, int size, String username, Integer status);

    /**
     * 逻辑删除用户
     *
     * @param id 用户ID
     */
    void delete(Long id);
}
