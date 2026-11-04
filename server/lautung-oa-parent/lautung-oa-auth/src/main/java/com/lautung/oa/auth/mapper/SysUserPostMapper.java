package com.lautung.oa.auth.mapper;

import com.lautung.oa.model.system.SysUserPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 用户岗位关联 Mapper（#248）。关联行只由用户写路径整体替换，不做岗位侧级联。
 */
public interface SysUserPostMapper extends BaseMapper<SysUserPost> {

    /**
     * 统计挂在岗位上的未删除用户数。两处存活谓词都归本方法所有：
     * 关联行随用户软删残留，用户软删后不算「仍在岗」，不能只数关联行（#248）。
     */
    @Select("SELECT COUNT(*) FROM sys_user_post up JOIN sys_user u ON u.id = up.user_id "
            + "WHERE up.post_id = #{postId} AND up.is_deleted = 0 AND u.is_deleted = 0")
    long countLiveUsersByPostId(@Param("postId") Long postId);
}
