package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendanceOvertime;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceOvertimeVo;
import org.apache.ibatis.annotations.Param;

/**
 * 加班单 Mapper（#282，ADR 0073）：办事「我的申请」只读本人，按 id 倒序分页；
 * 手写 SQL 的存活过滤显式带（ADR 0050）。单据表不存 process_id：审批摘要由
 * service 经 {@code BusinessApprovalReader} 拼装。
 */
public interface AttendanceOvertimeMapper extends BaseMapper<AttendanceOvertime> {

    IPage<AttendanceOvertimeVo> selectMyOvertimePage(Page<AttendanceOvertimeVo> page,
                                                     @Param("applicantId") Long applicantId,
                                                     @Param("query") AttendanceApplyQueryVo query);

    AttendanceOvertimeVo selectMyOvertimeDetail(@Param("id") Long id,
                                                @Param("applicantId") Long applicantId);
}
