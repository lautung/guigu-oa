package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysUserPost;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 用户岗位关联服务（#248）：写路径只做整体替换；用户页编辑回读见 {@link #findUserPostIds}（#249）。
 */
public interface SysUserPostService extends IService<SysUserPost> {

    /** 用提交集合替换该用户的岗位关联；空列表即清空，调用方负责区分「未传」。 */
    void replaceUserPosts(Long userId, List<Long> postIds);

    /** 该用户当前挂载的岗位 id（按关联行顺序）；无关联返回空列表。 */
    List<Long> findUserPostIds(Long userId);
}
