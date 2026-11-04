package com.lautung.oa.auth.service;

import com.lautung.oa.model.system.SysUserPost;
import com.baomidou.mybatisplus.spring.service.IService;

import java.util.List;

/**
 * 用户岗位关联服务（#248）：写路径只做整体替换，读侧留给用户页加料票（#249）。
 */
public interface SysUserPostService extends IService<SysUserPost> {

    /** 用提交集合替换该用户的岗位关联；空列表即清空，调用方负责区分「未传」。 */
    void replaceUserPosts(Long userId, List<Long> postIds);
}
