package cheer.service;

import cheer.entity.Menu;
import cheer.vo.MenuTreeVO;

import java.util.List;

public interface MenuService {
    List<MenuTreeVO> buildTree(List<Menu> menus);

    List<MenuTreeVO> tree();

    List<MenuTreeVO> userTree(Long userId);

    List<String> getPermissionsByUserId(Long userId);

}
