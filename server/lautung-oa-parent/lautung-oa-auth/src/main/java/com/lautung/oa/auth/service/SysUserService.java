package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.system.SysUserDetailVo;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;
import java.util.Map;

/**
 * <p>
 * 用户表 服务类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
public interface SysUserService extends IService<SysUser> {

    //更新状态
    void updateStatus(Long id, Integer status);

    //改密：写入新哈希并递增令牌版本，作废该用户此前签发的全部会话
    void changePassword(Long id, String encodedPassword);

    //头像对象键：只写 head_url 列；返回 false 表示用户行不存在
    boolean updateHeadUrl(Long id, String headUrl);

    //根据用户名进行查询
    SysUser getUserByUserName(String username);

    Map<String, Object> getCurrentUser();

    /** 用户编辑回读（#249）：实体列 + {@code sys_user_post} 的岗位集合；不存在返回 null。 */
    SysUserDetailVo findDetail(Long id);

    /**
     * 建号并挂岗位（同一事务，ADR 0034）：postIds 缺省/空列表表示不挂；
     * 部门存在且启用、任一岗位不存在或已停用都拒绝整个请求，不留下半个用户（#248/#249）。
     */
    void saveUser(SysUser user, List<Long> postIds);

    /**
     * 更新用户并同步岗位（同一事务）：postIds 未传（null）保留既有关联，
     * 空列表清空，非空列表整体替换；显式 deptId 要求存在且启用；非法时拒绝整个请求（#248/#249）。
     */
    void updateUser(SysUser user, List<Long> postIds);
}
