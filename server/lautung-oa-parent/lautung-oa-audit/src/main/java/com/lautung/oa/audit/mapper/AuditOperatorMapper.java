package com.lautung.oa.audit.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 操作人快照：审计只读 {@code sys_user}/{@code sys_dept} 的两列，为这份读取单独放一个小
 * Mapper，不依赖 auth 的 service/mapper（ADR 0054：audit 不依赖任何域模块）。
 */
public interface AuditOperatorMapper {

    /**
     * 按用户 id 取写入时的姓名、部门 id 与部门名。用户或部门已不存在时对应列为空，
     * 调用方保留登录凭证里的 username 作为兜底。
     */
    @Select("SELECT u.name AS user_name, u.dept_id AS dept_id, d.name AS dept_name "
            + "FROM sys_user u LEFT JOIN sys_dept d ON d.id = u.dept_id "
            + "WHERE u.id = #{userId}")
    OperatorSnapshot findOperatorSnapshot(@Param("userId") Long userId);
}
