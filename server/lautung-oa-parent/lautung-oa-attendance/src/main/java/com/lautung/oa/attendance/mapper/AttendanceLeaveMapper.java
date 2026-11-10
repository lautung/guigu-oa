package com.lautung.oa.attendance.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.lautung.oa.model.attendance.AttendanceLeave;
import com.lautung.oa.vo.attendance.AttendanceApplyQueryVo;
import com.lautung.oa.vo.attendance.AttendanceLeaveVo;
import org.apache.ibatis.annotations.Param;

/**
 * 请假单 Mapper（#282，ADR 0073）：办事「我的申请」只读本人，列表 join 假期类型名；
 * 手写 SQL 的存活过滤显式带（ADR 0050），排序 id 倒序（最新在前）。
 * 单据表不存 process_id：审批摘要由 service 经 {@code BusinessApprovalReader} 拼装。
 */
public interface AttendanceLeaveMapper extends BaseMapper<AttendanceLeave> {

    IPage<AttendanceLeaveVo> selectMyLeavePage(Page<AttendanceLeaveVo> page,
                                               @Param("applicantId") Long applicantId,
                                               @Param("query") AttendanceApplyQueryVo query);

    AttendanceLeaveVo selectMyLeaveDetail(@Param("id") Long id,
                                          @Param("applicantId") Long applicantId);
}
