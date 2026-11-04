package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysUserRole;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.Collection;

/**
 * <p>
 * 用户角色 服务类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
public interface SysUserRoleService extends IService<SysUserRole> {

    /**
     * 替换某个用户的角色集合（用户侧「分配角色」，#144）。{@code sys_user_role} 的唯一写入口，
     * 角色侧的 {@link #replaceRoleUsers(Long, Collection)} 与它共用同一张关系表。
     */
    void replaceUserRoles(Long userId, Collection<Long> roleIds);

    /**
     * 替换某个角色的成员集合（角色侧「分配用户」，#256）；空集合是合法的清空。
     */
    void replaceRoleUsers(Long roleId, Collection<Long> userIds);
}
