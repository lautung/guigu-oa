package com.lautung.oa.monitor.mapper;

import lombok.Data;

/**
 * 在线用户读侧的用户行快照（ADR 0060）：只取判定会话是否仍有效所需的四列。
 *
 * <p>刻意带上 {@code is_deleted} 而不依赖 {@code @TableLogic}：软删用户的历史会话仍挂在
 * Redis 里，读侧要能把它判成「已删」并跳过，而不是等 join 查不到。
 */
@Data
public class OnlineSessionUser {

    private Long id;

    private Integer isDeleted;

    private Integer status;

    private Long tokenVersion;

    /** 与 {@code SysUser.tokenVersionOrDefault()} 同口径：老行读作零。 */
    public long tokenVersionOrDefault() {
        return tokenVersion == null ? 0L : tokenVersion;
    }
}
