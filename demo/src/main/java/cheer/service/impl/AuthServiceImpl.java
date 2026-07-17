package cheer.service.impl;

import cheer.common.enums.ResultCode;
import cheer.common.exception.BusinessException;
import cheer.common.utils.JwtUtil;
import cheer.dto.LoginDTO;
import cheer.entity.User;
import cheer.mapper.UserMapper;
import cheer.service.AuthService;
import cheer.service.MenuService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AuthServiceImpl implements AuthService {
    @Autowired
    UserMapper userMapper;
    @Autowired
    MenuService menuService;
    @Autowired
    JwtUtil jwtUtil;
    @Autowired
    PasswordEncoder passwordEncoder;

    @Override
    public String login(LoginDTO dto) {
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, dto.getUsername())
                .eq(User::getDelFlag, 0)
                .eq(User::getStatus, 1);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        List<String> permissions = menuService.getPermissionsByUserId(user.getId());
        return jwtUtil.generateToken(user.getId(), user.getUsername(), permissions);
    }
}
