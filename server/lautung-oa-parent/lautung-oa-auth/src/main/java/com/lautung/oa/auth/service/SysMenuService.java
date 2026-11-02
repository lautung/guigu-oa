package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysMenu;
import com.lautung.oa.vo.system.AssginMenuVo;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * <p>
 * 菜单表 服务类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
public interface SysMenuService extends IService<SysMenu> {

    //菜单列表接口
    List<SysMenu> findNodes();

    //删除菜单
    void removeMenuById(Long id);

    //查询所有菜单和角色分配的菜单
    List<SysMenu> findMenuByRoleId(Long roleId);

    //角色分配菜单
    void doAssign(AssginMenuVo assginMenuVo);

    void saveMenu(SysMenu sysMenu);

    void updateMenu(SysMenu sysMenu);
}
