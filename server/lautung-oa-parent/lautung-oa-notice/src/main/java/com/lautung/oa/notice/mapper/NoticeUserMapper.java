package com.lautung.oa.notice.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 发布人姓名快照的唯一读取点（ADR 0055：发布时写进公告行，读侧不 join）。
 *
 * <p>notice 模块的 ArchUnit 边不允许访问 auth，所以不借 {@code SysUserService}；
 * 这里只读 {@code sys_user.name} 一列，不做任何认证授权判断。
 */
@Mapper
public interface NoticeUserMapper {

    @Select("SELECT name FROM sys_user WHERE id = #{userId} AND is_deleted = 0")
    String selectUserName(@Param("userId") Long userId);
}
