package cheer.controller;

import cheer.common.annotation.OperationLog;
import cheer.common.enums.ResultCode;
import cheer.common.result.Result;
import cheer.dto.UserDTO;
import cheer.entity.User;
import cheer.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;

    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<Page<User>> getUserPage(int current, int size, String username, Integer status) {
        Page<User> userPage = userService.query(current, size, username, status);
        return Result.success(userPage, ResultCode.SUCCESS, "查看成功");
    }

    @OperationLog(title = "用户管理", businessType = 1)
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('system:user:add')")
    Result<User> addUser(@Valid UserDTO userDTO) {
        User user = userService.add(userDTO);
        return Result.success(user, ResultCode.SUCCESS, "插入成功");
    }

    @OperationLog(title = "用户管理", businessType = 3)
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result deleteById(@PathVariable Long id) {
        userService.delete(id);
        return Result.success(id, ResultCode.SUCCESS, "删除成功");
    }
}
