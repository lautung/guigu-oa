package com.lautung.oa.auth.service.impl;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 用户表 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {

    //更新状态
    @Override
    public void updateStatus(Long id, Integer status) {
        //根据userid查询用户对象
        SysUser sysUser = baseMapper.selectById(id);
        //设置修改状态值
        sysUser.setStatus(status);
        //调用方法进行修改
        baseMapper.updateById(sysUser);
    }

    //改密：写入新哈希并递增令牌版本
    @Override
    public void changePassword(Long id, String encodedPassword) {
        baseMapper.changePassword(id, encodedPassword);
    }

    //根据用户名进行查询
    @Override
    public SysUser getUserByUserName(String username) {
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername,username);
        SysUser sysUser = baseMapper.selectOne(wrapper);
        return sysUser;
    }

    //用户名唯一（ADR 0042）：查重先行给出命名拒绝，并发漏网由唯一索引兜底并翻译成同一文案。
    @Override
    public boolean save(SysUser user) {
        ensureUsernameAvailable(user.getUsername(), null);
        try {
            return super.save(user);
        } catch (DuplicateKeyException duplicate) {
            throw duplicateUsername();
        }
    }

    //username 为 null 即不改名（MyBatis-Plus 空值策略），不查重。
    @Override
    public boolean updateById(SysUser user) {
        if (user.getUsername() != null) {
            ensureUsernameAvailable(user.getUsername(), user.getId());
        }
        try {
            return super.updateById(user);
        } catch (DuplicateKeyException duplicate) {
            throw duplicateUsername();
        }
    }

    //删除让位（ADR 0042）：软删行对查询不可见但仍占唯一索引，同事务先置空用户名再软删。
    @Transactional
    @Override
    public boolean removeById(Serializable id) {
        LambdaUpdateWrapper<SysUser> free = new LambdaUpdateWrapper<>();
        free.eq(SysUser::getId, id).set(SysUser::getUsername, null);
        baseMapper.update(null, free);
        return super.removeById(id);
    }

    private void ensureUsernameAvailable(String username, Long excludeId) {
        if (username == null) {
            return;
        }
        LambdaQueryWrapper<SysUser> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(SysUser::getUsername, username);
        if (excludeId != null) {
            wrapper.ne(SysUser::getId, excludeId);
        }
        if (baseMapper.selectCount(wrapper) > 0) {
            throw duplicateUsername();
        }
    }

    private static BusinessException duplicateUsername() {
        return new BusinessException(201, "用户名已存在");
    }

    @Override
    public Map<String, Object> getCurrentUser() {
        SysUser sysUser = baseMapper.selectById(LoginUserInfoHelper.getUserId());
        Map<String, Object> map = new HashMap<>();
        map.put("name", sysUser.getName());
        map.put("phone", sysUser.getPhone());
        return map;
    }
}
