package com.atguigu.auth.service;

import com.atguigu.model.system.SysUser;
import com.baomidou.mybatisplus.spring.service.IService;

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

    //根据用户名进行查询
    SysUser getUserByUserName(String username);

    Map<String, Object> getCurrentUser();
}
