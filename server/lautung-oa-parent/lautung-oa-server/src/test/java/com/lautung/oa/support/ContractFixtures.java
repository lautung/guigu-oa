package com.lautung.oa.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 合同测试共用的夹具复位（JDBC 部分）：种子用户复位到种子形状、清掉测试自建的临时角色。
 * Redis 侧由各套件用 {@link InMemoryRedisTemplate#clear()} 自理。
 */
public final class ContractFixtures {

    private ContractFixtures() {
    }

    /** 种子用户 1/2（admin、user）复位：status=1、未删、token_version=0。 */
    public static void resetSeededUsers(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.update("UPDATE sys_user SET status = 1, is_deleted = 0, token_version = 0 "
                + "WHERE id IN (1, 2)");
    }

    /** 清掉合同测试自建的临时只读角色（角色本体、用户授予、菜单授予三处）。 */
    public static void deleteAdHocRole(JdbcTemplate jdbcTemplate, long roleId) {
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE role_id = ?", roleId);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", roleId);
    }
}
