package cheer.service;

import cheer.dto.UserDTO;
import cheer.entity.User;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

public interface UserService {
    User add(UserDTO userDTO);

    Page<User> query(int current, int size, String username, Integer status);

    void delete(Long id);
}
