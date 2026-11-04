package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.mapper.SysPostMapper;
import com.lautung.oa.auth.mapper.SysUserPostMapper;
import com.lautung.oa.auth.service.SysPostService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysPost;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class SysPostServiceImpl extends ServiceImpl<SysPostMapper, SysPost> implements SysPostService {

    @Autowired
    private SysUserPostMapper sysUserPostMapper;

    @Override
    public void updatePost(SysPost post) {
        if (post.getId() == null || getById(post.getId()) == null) {
            throw new BusinessException(201, "岗位不存在");
        }
        if (!updateById(post)) {
            throw new BusinessException(201, "岗位不存在");
        }
    }

    @Override
    public void removePost(Long id) {
        if (id == null || getById(id) == null) {
            throw new BusinessException(201, "岗位不存在");
        }
        // 只统计未删除用户：存活谓词收敛在 mapper 方法里，service 不再拼 SQL（#248）。
        if (sysUserPostMapper.countLiveUsersByPostId(id) > 0) {
            throw new BusinessException(201, "岗位已分配用户，不能删除");
        }
        if (!removeById(id)) {
            throw new BusinessException(201, "岗位不存在");
        }
    }

    @Override
    public List<Long> validateAssignablePostIds(List<Long> postIds) {
        List<Long> normalized = postIds == null ? List.of() : postIds.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new))
                .stream()
                .toList();
        if (normalized.isEmpty()) {
            return List.of();
        }
        List<SysPost> posts = baseMapper.selectBatchIds(normalized);
        if (posts.size() != normalized.size()) {
            throw new BusinessException(201, "岗位不存在");
        }
        if (posts.stream().anyMatch(post -> post.getStatus() == null || post.getStatus() != 1)) {
            throw new BusinessException(201, "岗位已停用");
        }
        return normalized;
    }

    @Override
    public List<SysPost> findEnabledOptions() {
        return list(new LambdaQueryWrapper<SysPost>()
                .eq(SysPost::getStatus, 1)
                .orderByAsc(SysPost::getSortValue)
                .orderByAsc(SysPost::getId));
    }
}
