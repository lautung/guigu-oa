package com.lautung.oa.auth.service.impl;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysDept;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.auth.mapper.SysDeptMapper;
import com.lautung.oa.auth.mapper.SysUserMapper;
import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.auth.service.SysUserPostService;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.system.SysUserDetailVo;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.Serializable;
import java.util.HashMap;
import java.util.List;
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

    @Autowired
    private SysPostService sysPostService;

    @Autowired
    private SysUserPostService sysUserPostService;

    @Autowired
    private SysDeptMapper sysDeptMapper;

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

    //头像对象键只写 head_url 列，走实体更新让公共字段处理器刷新 update_time（ADR 0031）。
    @Override
    public boolean updateHeadUrl(Long id, String headUrl) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setHeadUrl(headUrl);
        return baseMapper.updateById(user) > 0;
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

    /**
     * 建号与挂岗位同一事务（#248）：部门与岗位先校验，非法集合不留下半个用户；
     * postIds 缺省与空列表同义，都表示不挂岗位（ADR 0034 的 save 语义）。
     */
    @Transactional
    @Override
    public void saveUser(SysUser user, List<Long> postIds) {
        ensureDeptAssignable(user.getDeptId());
        List<Long> normalized = sysPostService.validateAssignablePostIds(postIds == null ? List.of() : postIds);
        if (!save(user)) {
            throw new BusinessException(201, "用户保存失败");
        }
        if (!normalized.isEmpty()) {
            sysUserPostService.replaceUserPosts(user.getId(), normalized);
        }
    }

    /**
     * 更新与岗位同步同一事务（#248）：postIds 未传保留、空列表清空、非空替换；
     * 显式 deptId 校验存在且启用（#249），非法集合不改用户行也不改关联行。
     */
    @Transactional
    @Override
    public void updateUser(SysUser user, List<Long> postIds) {
        ensureDeptAssignable(user.getDeptId());
        List<Long> normalized = postIds == null ? null : sysPostService.validateAssignablePostIds(postIds);
        if (!updateById(user)) {
            throw new BusinessException(201, "用户不存在");
        }
        if (normalized != null) {
            sysUserPostService.replaceUserPosts(user.getId(), normalized);
        }
    }

    @Override
    public SysUserDetailVo findDetail(Long id) {
        SysUser user = id == null ? null : getById(id);
        if (user == null) {
            return null;
        }
        SysUserDetailVo detail = new SysUserDetailVo();
        detail.setId(user.getId());
        detail.setUsername(user.getUsername());
        detail.setName(user.getName());
        detail.setPhone(user.getPhone());
        detail.setDeptId(user.getDeptId());
        detail.setStatus(user.getStatus());
        detail.setCreateTime(user.getCreateTime());
        detail.setPostIds(sysUserPostService.findUserPostIds(user.getId()));
        return detail;
    }

    /** 部门是用户归属的唯一来源（ADR 0057）：空表示不归属；存在但停用不允许新归属或改挂。 */
    private void ensureDeptAssignable(Long deptId) {
        if (deptId == null) {
            return;
        }
        SysDept dept = sysDeptMapper.selectById(deptId);
        if (dept == null) {
            throw new BusinessException(201, "部门不存在");
        }
        if (dept.getStatus() == null || dept.getStatus() != 1) {
            throw new BusinessException(201, "部门已停用");
        }
    }
}
