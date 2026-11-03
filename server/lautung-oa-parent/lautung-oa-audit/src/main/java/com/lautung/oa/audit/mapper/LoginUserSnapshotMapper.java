package com.lautung.oa.audit.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 登录日志的用户快照：按提交的 username 解析 {@code user_id}/{@code dept_id}，查不到留空。
 * 和 {@link AuditOperatorMapper} 一样，audit 用自己的小 Mapper，不依赖 auth 的 service。
 */
public interface LoginUserSnapshotMapper {

    @Select("SELECT u.id AS user_id, u.dept_id AS dept_id "
            + "FROM sys_user u WHERE u.username = #{username} AND u.is_deleted = 0 LIMIT 1")
    LoginUserSnapshot findLoginUserSnapshot(@Param("username") String username);
}
