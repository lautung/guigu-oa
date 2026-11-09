package com.lautung.oa.attendance.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.vo.attendance.AttendanceGroupSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceGroupUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceMemberVo;

import java.util.List;

/**
 * 考勤组 CRUD（#278，ADR 0073）：绑一个班次、每周工作日多选、成员直接选人。
 * 成员关系一人最多一组（唯一索引兜底）；成员移除是硬删；组删除连带清成员行。
 */
public interface AttendanceGroupService extends IService<AttendanceGroup> {

    void saveGroup(AttendanceGroupSaveRequest request);

    void updateGroup(AttendanceGroupUpdateRequest request);

    void removeGroup(Long id);

    List<AttendanceMemberVo> findMembers(Long groupId);
}
