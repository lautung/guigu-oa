package com.lautung.oa.audit.mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 操作人快照：审计只读 {@code sys_user}/{@code sys_dept} 的两列，为这份读取单独放一个小
 * Mapper，不依赖 auth 的 service/mapper（ADR 0054：audit 不依赖任何域模块）。
 *
 * <p>刻意不过滤 {@code sys_user.is_deleted}/{@code sys_dept.is_deleted}：操作日志是历史
 * 事实，写入时的操作人与部门快照不因后来删除而改变。{@link LoginUserSnapshotMapper}
 * 过滤 {@code is_deleted = 0} 是另一种语义——它解析的是「当前登录账号」，已删账号不应
 * 再被当成登录人。两者不能合并成一条查询。
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
