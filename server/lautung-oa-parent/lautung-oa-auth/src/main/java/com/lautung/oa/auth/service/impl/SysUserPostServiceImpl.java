package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.mapper.SysUserPostMapper;
import com.lautung.oa.auth.service.SysUserPostService;
import com.lautung.oa.model.system.SysUserPost;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SysUserPostServiceImpl extends ServiceImpl<SysUserPostMapper, SysUserPost>
        implements SysUserPostService {

    @Override
    @Transactional
    public void replaceUserPosts(Long userId, List<Long> postIds) {
        LambdaQueryWrapper<SysUserPost> existing = new LambdaQueryWrapper<>();
        existing.eq(SysUserPost::getUserId, userId);
        remove(existing);
        for (Long postId : postIds) {
            SysUserPost relation = new SysUserPost();
            relation.setUserId(userId);
            relation.setPostId(postId);
            save(relation);
        }
    }
}
