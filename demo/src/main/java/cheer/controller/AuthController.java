package cheer.controller;

import cheer.common.enums.ResultCode;
import cheer.common.result.Result;
import cheer.dto.LoginDTO;
import cheer.service.AuthService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@Slf4j
public class AuthController {
    @Autowired
    AuthService authService;

    @PostMapping("/login")
    public Result<String> login(LoginDTO loginDTO) {
        String token = authService.login(loginDTO);
        log.info("用户登录成功, username: {}", loginDTO.getUsername());
        return Result.success(token, ResultCode.SUCCESS, "登录成功");
    }
}
