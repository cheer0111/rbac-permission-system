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


@Service
public class UserServiceImpl implements UserService {
    @Autowired
    UserMapper userMapper;
    @Autowired
    PasswordEncoder passwordEncoder;

    public User add(UserDTO userDTO) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper();
        queryWrapper.eq(User::getUsername, userDTO.getUsername());
        Long count = userMapper.selectCount(queryWrapper);
        if (count != null && count > 0) {
            throw new BusinessException(ResultCode.DATA_EXISTS, "用户名已存在");
        }
        User user = new User();
        BeanUtils.copyProperties(userDTO, user);
        user.setPassword(passwordEncoder.encode(userDTO.getPassword()));
        user.setAvatar(null);
        user.setDelFlag(0);
        userMapper.insert(user);
        return user;
    }

    public Page<User> query(int current, int size, String username, Integer status) {
        Page<User> page = new Page<>(current, size);
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StringUtils.isNotBlank(username)) {
            queryWrapper.like(User::getUsername, username);
        }
        if (status != null) {
            queryWrapper.eq(User::getStatus, status);
        }
        return userMapper.selectPage(page, queryWrapper);
    }

    public void delete(Long id) {
        userMapper.deleteById(id);
    }
}
