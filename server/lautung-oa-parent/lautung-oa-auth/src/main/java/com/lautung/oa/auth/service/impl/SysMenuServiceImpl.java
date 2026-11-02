package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.grant.BackofficeGrant;
import com.lautung.oa.auth.service.SysRoleMenuService;
import com.lautung.oa.auth.service.PermissionCacheInvalidator;
import com.lautung.oa.auth.utils.MenuHelper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysMenu;
import com.lautung.oa.auth.mapper.SysMenuMapper;
import com.lautung.oa.auth.service.SysMenuService;
import com.lautung.oa.model.system.SysRoleMenu;
import com.lautung.oa.vo.system.AssginMenuVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 菜单表 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
@Service
public class SysMenuServiceImpl extends ServiceImpl<SysMenuMapper, SysMenu> implements SysMenuService {

    private static final Set<String> PROTECTED_MENU_PATHS = Set.of(
            "/system/sysUser", "/system/sysRole", "/system/sysMenu", "/system", "/processSet", "/processMgr");

    @Autowired
    private SysRoleMenuService sysRoleMenuService;

    @Autowired
    private PermissionCacheInvalidator permissionCacheInvalidator;

    @Autowired
    private BackofficeGrant backofficeGrant;

    @Override
    @Transactional
    public boolean save(SysMenu menu) {
        boolean saved = baseMapper.insert(menu) > 0;
        if (saved) {
            permissionCacheInvalidator.invalidateMenuMembersAfterCommit(menu.getId());
        }
        return saved;
    }

    @Override
    @Transactional
    public boolean updateById(SysMenu menu) {
        boolean updated = baseMapper.updateById(menu) > 0;
        if (updated) {
            permissionCacheInvalidator.invalidateMenuMembersAfterCommit(menu.getId());
        }
        return updated;
    }

    //菜单列表接口
    @Override
    public List<SysMenu> findNodes() {
        return MenuHelper.buildTree(backofficeGrant.backofficeMenus());
    }

    //删除菜单
    @Override
    @Transactional
    public void removeMenuById(Long id) {
        SysMenu menu = baseMapper.selectById(id);
        if (menu != null && isProtectedMenuPath(menu.getPath())) {
            throw new BusinessException(201, "配置菜单入口不能删除");
        }

        //判断当前菜单是否有下一层菜单
        LambdaQueryWrapper<SysMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysMenu::getParentId,id);
        Long count = baseMapper.selectCount(wrapper);
        if(count > 0) {
            throw new BusinessException(201,"菜单不能删除");
        }
        boolean removed = baseMapper.deleteById(id) > 0;
        if (removed) {
            permissionCacheInvalidator.invalidateMenuMembersAfterCommit(id);
        }
    }

    //查询所有菜单和角色分配的菜单
    @Override
    public List<SysMenu> findMenuByRoleId(Long roleId) {
        //1 只取配置类菜单，并只保留启用项
        List<SysMenu> allSysMenuList = backofficeGrant.backofficeMenus().stream()
                .filter(item -> Objects.equals(item.getStatus(), 1))
                .collect(Collectors.toList());

        //2 根据角色id roleId查询 角色菜单关系表里面 角色id对应所有的菜单id
        LambdaQueryWrapper<SysRoleMenu> wrapperSysRoleMenu = new LambdaQueryWrapper<>();
        wrapperSysRoleMenu.eq(SysRoleMenu::getRoleId,roleId);
        List<SysRoleMenu> sysRoleMenuList = sysRoleMenuService.list(wrapperSysRoleMenu);

        //3 根据获取菜单id，获取对应菜单对象
        List<Long> menuIdList = sysRoleMenuList.stream().map(c -> c.getMenuId()).collect(Collectors.toList());

        //3.1 拿着菜单id 和所有菜单集合里面id进行比较，如果相同封装
        allSysMenuList.stream().forEach(item -> {
            if(menuIdList.contains(item.getId())) {
                item.setSelect(true);
            } else {
                item.setSelect(false);
            }
        });

        //4 返回规定树形显示格式菜单列表
        List<SysMenu> sysMenuList = MenuHelper.buildTree(allSysMenuList);
        return sysMenuList;
    }

    //为角色分配菜单
    @Override
    @Transactional
    public void doAssign(AssginMenuVo assginMenuVo) {
        LambdaQueryWrapper<SysRoleMenu> currentRoleMenus = new LambdaQueryWrapper<>();
        currentRoleMenus.eq(SysRoleMenu::getRoleId, assginMenuVo.getRoleId());
        Set<Long> existingMenuIds = sysRoleMenuService.list(currentRoleMenus).stream()
                .map(SysRoleMenu::getMenuId)
                .collect(Collectors.toSet());

        List<Long> requestedMenuIds = assginMenuVo.getMenuIdList();
        List<Long> assignableMenuIds = new ArrayList<>();
        if (requestedMenuIds != null) {
            for (Long menuId : requestedMenuIds) {
                if (menuId == null) {
                    continue;
                }

                SysMenu menu = baseMapper.selectById(menuId);
                if (!backofficeGrant.isGrantableBackofficeMenu(menu)) {
                    // Drop legacy non-config-menu grants when the role is resubmitted.
                    // A newly requested invalid ID still rejects the whole assignment.
                    if (existingMenuIds.contains(menuId)) {
                        continue;
                    }
                    throw new BusinessException(201, "只能授予配置菜单");
                }
                assignableMenuIds.add(menuId);
            }
        }

        //1 根据角色id 删除菜单角色表 分配数据
        LambdaQueryWrapper<SysRoleMenu> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysRoleMenu::getRoleId,assginMenuVo.getRoleId());
        sysRoleMenuService.remove(wrapper);

        //2 从参数里面获取角色新分配菜单id列表，
        // 进行遍历，把每个id数据添加菜单角色表
        List<Long> menuIdList = assignableMenuIds;
        for(Long menuId:menuIdList) {
            if(StringUtils.isEmpty(menuId)) {
                continue;
            }
            SysRoleMenu sysRoleMenu = new SysRoleMenu();
            sysRoleMenu.setMenuId(menuId);
            sysRoleMenu.setRoleId(assginMenuVo.getRoleId());
            sysRoleMenuService.save(sysRoleMenu);
        }
        permissionCacheInvalidator.invalidateRoleMembersAfterCommit(assginMenuVo.getRoleId());
    }

    @Override
    public void saveMenu(SysMenu sysMenu) {
        if (sysMenu != null && isProtectedMenuPath(sysMenu.getPath())) {
            throw new BusinessException(201, "菜单路由不可重定义");
        }
        if (sysMenu == null || !backofficeGrant.isBackofficeMenu(sysMenu)) {
            throw new BusinessException(201, "只能维护配置菜单");
        }
        validateButtonShape(sysMenu, null);
        save(sysMenu);
    }

    @Override
    public void updateMenu(SysMenu sysMenu) {
        if (sysMenu == null || !backofficeGrant.isBackofficeMenu(sysMenu)) {
            throw new BusinessException(201, "只能维护配置菜单");
        }
        SysMenu existingMenu = sysMenu.getId() == null ? null : baseMapper.selectById(sysMenu.getId());
        String oldPath = existingMenu == null ? null : existingMenu.getPath();
        String newPath = sysMenu.getPath();
        boolean reservedPathIsChanging = isProtectedMenuPath(newPath)
                && !Objects.equals(oldPath, newPath);
        boolean reservedMenuIdentityIsChanging = isProtectedMenuPath(oldPath)
                && (!Objects.equals(oldPath, newPath)
                || !Objects.equals(existingMenu.getType(), sysMenu.getType())
                || !Objects.equals(existingMenu.getParentId(), sysMenu.getParentId()));
        boolean reservedMenuIsBeingDisabled = isProtectedMenuPath(oldPath)
                && sysMenu.getStatus() != null
                && !Objects.equals(sysMenu.getStatus(), 1);
        if (reservedPathIsChanging || reservedMenuIdentityIsChanging || reservedMenuIsBeingDisabled) {
            throw new BusinessException(201, "菜单路由不可重定义");
        }
        // 请求里不发 component 即保留旧行，边界判不了目录转菜单后的最终形状，这里按落库结果判（ADR 0036）。
        if (landsAsPageWithoutComponent(sysMenu, existingMenu)) {
            throw new BusinessException(201, "类型为菜单时组件路径不能为空");
        }
        validateButtonShape(sysMenu, existingMenu);
        updateById(sysMenu);
    }

    /**
     * 按钮点的落库形状（ADR 0056）：必须挂在页面上，perms 必填，path/component 为空。
     * 更新请求省略的字段沿用旧行，因此按最终形状判，而不是只看请求体。
     */
    private void validateButtonShape(SysMenu update, SysMenu existing) {
        Integer type = update.getType() != null
                ? update.getType()
                : (existing == null ? null : existing.getType());
        if (!Objects.equals(type, 2)) {
            return;
        }
        Long parentId = update.getParentId() != null
                ? update.getParentId()
                : (existing == null ? null : existing.getParentId());
        SysMenu parent = parentId == null ? null : baseMapper.selectById(parentId);
        if (parent == null || !Objects.equals(parent.getType(), 1)) {
            throw new BusinessException(201, "按钮必须挂在页面下");
        }
        String perms = update.getPerms() != null
                ? update.getPerms()
                : (existing == null ? null : existing.getPerms());
        String path = update.getPath() != null
                ? update.getPath()
                : (existing == null ? null : existing.getPath());
        String component = update.getComponent() != null
                ? update.getComponent()
                : (existing == null ? null : existing.getComponent());
        if (!StringUtils.hasText(perms)) {
            throw new BusinessException(201, "按钮权限标识不能为空");
        }
        if (StringUtils.hasText(path)) {
            throw new BusinessException(201, "按钮的路由地址必须为空");
        }
        if (StringUtils.hasText(component)) {
            throw new BusinessException(201, "按钮的组件路径必须为空");
        }
    }

    /** 更新落库后的最终形状：请求字段为空沿用旧行。目录转成菜单页而组件仍为空时拒绝。 */
    private boolean landsAsPageWithoutComponent(SysMenu update, SysMenu existing) {
        Integer type = update.getType() != null
                ? update.getType()
                : (existing == null ? null : existing.getType());
        String component = update.getComponent() != null
                ? update.getComponent()
                : (existing == null ? null : existing.getComponent());
        return Objects.equals(type, 1) && !StringUtils.hasText(component);
    }

    private boolean isProtectedMenuPath(String path) {
        return path != null && PROTECTED_MENU_PATHS.contains(path);
    }

}
