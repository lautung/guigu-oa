package com.lautung.oa.auth.mapper;

import com.lautung.oa.model.system.SysUser;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * <p>
 * 用户表 Mapper 接口
 * </p>
 *
 * @author atguigu
 * @since 2023-02-02
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * Writes the new password and retires the old tokens in one statement: the increment reads
     * the row's current version, so concurrent password changes cannot lose or repeat a bump.
     */
    @Update("UPDATE sys_user SET password = #{password}, token_version = token_version + 1, "
            + "update_time = CURRENT_TIMESTAMP WHERE id = #{id} AND is_deleted = 0")
    int changePassword(@Param("id") Long id, @Param("password") String password);
}
