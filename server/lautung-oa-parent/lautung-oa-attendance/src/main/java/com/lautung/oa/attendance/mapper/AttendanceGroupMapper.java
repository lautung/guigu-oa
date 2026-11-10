package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.vo.attendance.AttendanceGroupVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 考勤组 Mapper（#278，ADR 0073）：列表行 join 班次名称与成员数（展示走 join），
 * 手写 SQL 的存活过滤每条显式带（ADR 0050）。
 */
public interface AttendanceGroupMapper extends BaseMapper<AttendanceGroup> {

    @Select("<script>"
            + "SELECT g.id, g.name, g.shift_id AS shiftId, s.name AS shiftName, "
            + "g.workdays, COUNT(m.id) AS memberCount, g.remark, g.create_time AS createTime "
            + "FROM attendance_group g "
            + "LEFT JOIN attendance_shift s ON s.id = g.shift_id AND s.is_deleted = 0 "
            + "LEFT JOIN attendance_group_member m ON m.group_id = g.id "
            + "WHERE g.is_deleted = 0 "
            + "<if test='name != null and name != \"\"'> AND g.name LIKE CONCAT('%', #{name}, '%')</if> "
            + "GROUP BY g.id, g.name, g.shift_id, s.name, g.workdays, g.remark, g.create_time "
            + "ORDER BY g.id DESC"
            + "</script>")
    IPage<AttendanceGroupVo> selectGroupPage(Page<AttendanceGroupVo> page, @Param("name") String name);
}
