package com.lautung.oa.monitor.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Collection;
import java.util.List;

/**
 * 在线用户读侧的用户快照查询（ADR 0060）：monitor 不依赖 auth 的 service，
 * 用自己的只读 Mapper 按登记里的 userId 批量取现值。
 */
public interface OnlineSessionUserMapper {

    @Select("""
            <script>
            SELECT u.id AS id, u.is_deleted AS is_deleted, u.status AS status,
                   u.token_version AS token_version
            FROM sys_user u
            WHERE u.id IN
            <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
            </script>
            """)
    List<OnlineSessionUser> findByIds(@Param("ids") Collection<Long> ids);
}
