package cheer.service.impl;

import cheer.common.enums.ResultCode;
import cheer.common.exception.BusinessException;
import cheer.dto.UserDTO;
import cheer.entity.User;
import cheer.mapper.UserMapper;
import cheer.service.UserService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.springframework.beans.BeanUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 用户服务实现
 */
@Service
public class UserServiceImpl implements UserService {
    @Autowired
    UserMapper userMapper;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    public User add(UserDTO userDTO) {
        // 用户名查重
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(User::getUsername, userDTO.getUsername());
        Long count = userMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "用户名已存在");
        }

        // DTO → Entity，密码用 BCrypt 加密
        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setAvatar(null);
        user.setDelFlag(0);
        userMapper.insert(user);
        return user;
    }

    @Override
    public Page<User> query(int current, int size, String username, Integer status) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        // 动态条件拼接：有值才加条件
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like(User::getUsername, username);
        }
        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }
        return userMapper.selectPage(page, queryWrapper);
    }

    @Override
    public void delete(Long id) {
        // MP 的 @TableLogic 会自动将 deleteById 转为逻辑删除（UPDATE SET del_flag=1）
        userMapper.deleteById(id);
    }
}
