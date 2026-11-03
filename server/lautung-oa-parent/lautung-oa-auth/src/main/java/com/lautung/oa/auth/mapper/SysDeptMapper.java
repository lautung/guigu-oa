package com.lautung.oa.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.system.SysDept;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 部门 Mapper。数据范围「本部门及以下」按 {@code ancestors} 的包裹逗号路径匹配（ADR 0056/0057）：
 * 部门 3 的子树是 {@code LIKE '%,3,%'}，自身因为路径含自身也落在结果里。
 * 手写 SQL 不吃实体的 {@code @TableLogic}，此处显式过滤软删行。
 */
public interface SysDeptMapper extends BaseMapper<SysDept> {

    @Select("SELECT id FROM sys_dept WHERE is_deleted = 0 "
            + "AND ancestors LIKE CONCAT('%,', #{deptId}, ',%')")
    List<Long> selectSubtreeIds(@Param("deptId") Long deptId);
}
