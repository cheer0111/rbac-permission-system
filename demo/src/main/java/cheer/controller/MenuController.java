package cheer.controller;

import cheer.common.enums.ResultCode;
import cheer.common.result.Result;
import cheer.service.MenuService;
import cheer.vo.MenuTreeVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 菜单控制器
 */
@RestController
@RequestMapping("/menu")
public class MenuController {
    @Autowired
    private MenuService menuService;

    /**
     * 获取全量菜单树（管理员视角）
     */
    @GetMapping("/tree")
    public Result<List<MenuTreeVO>> list() {
        List<MenuTreeVO> vos = menuService.tree();
        return Result.success(vos, ResultCode.SUCCESS, "查看成功");
    }

    /**
     * 获取指定用户的动态菜单树（根据角色过滤）
     *
     * @param id 用户ID
     */
    @GetMapping("/tree/user/{id}")
    public Result<List<MenuTreeVO>> userList(@PathVariable Long id) {
        List<MenuTreeVO> vos = menuService.userTree(id);
        return Result.success(vos, ResultCode.SUCCESS, "查看成功");
    }
}
