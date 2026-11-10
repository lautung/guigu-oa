package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.vo.attendance.AttendanceMemberVo;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 考勤组成员关系 Mapper（#278，ADR 0073）：纯关系表硬删；成员行 join sys_user
 * 取姓名展示（attendance 自有 mapper 小读，不依赖 auth）。
 */
public interface AttendanceGroupMemberMapper extends BaseMapper<AttendanceGroupMember> {

    @Select("SELECT m.id, m.user_id AS userId, u.username, u.name "
            + "FROM attendance_group_member m "
            + "JOIN sys_user u ON u.id = m.user_id "
            + "WHERE m.group_id = #{groupId} "
            + "ORDER BY u.username, u.id")
    List<AttendanceMemberVo> selectMemberRows(@Param("groupId") Long groupId);
}
