package com.lautung.oa.auth.mapper;

import com.lautung.oa.model.system.SysUserPost;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * 用户岗位关联 Mapper（#248）。关联行只由用户写路径整体替换，不做岗位侧级联。
 */
public interface SysUserPostMapper extends BaseMapper<SysUserPost> {
}
