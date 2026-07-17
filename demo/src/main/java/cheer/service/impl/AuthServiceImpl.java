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

/**
 * 认证服务实现
 * <p>
 * 登录流程：查用户 → 校验密码 → 查权限列表 → 生成 JWT token
 */
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
        // 查询未删除且已启用的用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUsername, dto.getUsername())
                .eq(User::getDelFlag, 0)
                .eq(User::getStatus, 1);
        User user = userMapper.selectOne(queryWrapper);

        // 统一提示"用户名或密码错误"，不暴露具体原因，防止恶意枚举
        if (user == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }
        if (!passwordEncoder.matches(dto.getPassword(), user.getPassword())) {
            throw new BusinessException(ResultCode.UNAUTHORIZED, "用户名或密码错误");
        }

        // 查询该用户的权限标识列表，写入 token
        List<String> permissions = menuService.getPermissionsByUserId(user.getId());
        return jwtUtil.generateToken(user.getId(), user.getUsername(), permissions);
    }
}
