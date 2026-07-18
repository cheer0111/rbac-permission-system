package cheer.controller;

import cheer.common.annotation.OperationLog;
import cheer.common.enums.ResultCode;
import cheer.common.result.Result;
import cheer.dto.UserDTO;
import cheer.entity.User;
import cheer.service.FileService;
import cheer.service.UserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户管理控制器
 */
@RestController
@RequestMapping("/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Autowired
    private FileService fileService;

    /**
     * 用户分页查询
     */
    @GetMapping("/page")
    @PreAuthorize("hasAuthority('system:user:query')")
    public Result<Page<User>> getUserPage(int current, int size, String username, Integer status) {
        Page<User> userPage = userService.query(current, size, username, status);
        return Result.success(userPage, ResultCode.SUCCESS, "查看成功");
    }

    /**
     * 新增用户
     */
    @OperationLog(title = "用户管理", businessType = 1)
    @PostMapping("/add")
    @PreAuthorize("hasAuthority('system:user:add')")
    Result<User> addUser(@Valid UserDTO userDTO) {
        User user = userService.add(userDTO);
        return Result.success(user, ResultCode.SUCCESS, "插入成功");
    }

    /**
     * 根据ID删除用户（逻辑删除）
     */
    @OperationLog(title = "用户管理", businessType = 3)
    @DeleteMapping("/delete/{id}")
    @PreAuthorize("hasAuthority('system:user:delete')")
    public Result deleteById(@PathVariable Long id) {
        userService.delete(id);
        return Result.success(id, ResultCode.SUCCESS, "删除成功");
    }

    /**
     * 用户头像上传
     */
    @PostMapping("/avatar")
    public Result<String> uploadAvatar(@RequestParam MultipartFile avatar) {
        String url = fileService.store(avatar);
        Long userId = (Long) SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getPrincipal();
        userService.upload(url, userId);
        return Result.success(url, ResultCode.SUCCESS, "上传成功");
    }
}
