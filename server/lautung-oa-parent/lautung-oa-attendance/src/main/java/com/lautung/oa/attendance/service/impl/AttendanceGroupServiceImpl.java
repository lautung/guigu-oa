package com.lautung.oa.attendance.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.lautung.oa.attendance.mapper.AttendanceGroupMapper;
import com.lautung.oa.attendance.mapper.AttendanceGroupMemberMapper;
import com.lautung.oa.attendance.mapper.AttendanceShiftMapper;
import com.lautung.oa.attendance.mapper.AttendanceUserMapper;
import com.lautung.oa.attendance.service.AttendanceGroupService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.attendance.AttendanceGroup;
import com.lautung.oa.model.attendance.AttendanceGroupMember;
import com.lautung.oa.model.attendance.AttendanceShift;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.attendance.AttendanceGroupSaveRequest;
import com.lautung.oa.vo.attendance.AttendanceGroupUpdateRequest;
import com.lautung.oa.vo.attendance.AttendanceMemberVo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 考勤组 CRUD 实现（#278，ADR 0073）。
 *
 * <p>成员替换语义对齐 #249 的 postIds 约定：save 缺省与空列表都建空组；
 * update 缺省保留、显式列表（含空列表）整体替换。成员必须存在且启用
 * （attendance 自有 mapper 读 sys_user，不依赖 auth）；一人最多一组先查后插，
 * 并发漏网由 {@code uk_attendance_member_user} 兜底并翻译成同一文案（ADR 0042 同族）。
 * 成员移除与组删除都硬删关系行，审计走操作日志。
 */
@Service
public class AttendanceGroupServiceImpl extends ServiceImpl<AttendanceGroupMapper, AttendanceGroup>
        implements AttendanceGroupService {

    @Autowired
    private AttendanceGroupMemberMapper memberMapper;

    @Autowired
    private AttendanceShiftMapper shiftMapper;

    @Autowired
    private AttendanceUserMapper attendanceUserMapper;

    @Override
    @Transactional
    public void saveGroup(AttendanceGroupSaveRequest request) {
        ensureShiftExists(request.getShiftId());
        AttendanceGroup group = request.toEntity();
        group.setWorkdays(canonicalWorkdays(request.getWorkdays()));
        this.save(group);
        replaceMembers(group.getId(), request.memberUserIdsOrEmpty(), group.getId());
    }

    @Override
    @Transactional
    public void updateGroup(AttendanceGroupUpdateRequest request) {
        AttendanceGroup existing = getById(request.getId());
        if (existing == null) {
            throw new BusinessException(201, "考勤组不存在");
        }
        if (request.getShiftId() != null) {
            ensureShiftExists(request.getShiftId());
        }
        AttendanceGroup group = request.toEntity();
        if (request.getWorkdays() != null) {
            group.setWorkdays(canonicalWorkdays(request.getWorkdays()));
        }
        updateById(group);
        if (request.hasMemberUserIds()) {
            replaceMembers(request.getId(), request.memberUserIdsOrEmpty(), request.getId());
        }
    }

    @Override
    @Transactional
    public void removeGroup(Long id) {
        removeById(id);
        // 成员是纯关系表：组没了关系行也没意义，硬删不留孤儿行。
        memberMapper.delete(new LambdaQueryWrapper<AttendanceGroupMember>()
                .eq(AttendanceGroupMember::getGroupId, id));
    }

    @Override
    public List<AttendanceMemberVo> findMembers(Long groupId) {
        return memberMapper.selectMemberRows(groupId);
    }

    private void replaceMembers(Long groupId, List<Long> memberUserIds, Long ownGroupId) {
        Set<Long> userIds = new LinkedHashSet<>(
                memberUserIds == null ? List.of() : memberUserIds);
        if (userIds.isEmpty()) {
            memberMapper.delete(new LambdaQueryWrapper<AttendanceGroupMember>()
                    .eq(AttendanceGroupMember::getGroupId, groupId));
            return;
        }

        List<SysUser> users = attendanceUserMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .in(SysUser::getId, userIds));
        List<Long> enabled = users.stream()
                .filter(user -> user.getStatus() != null && user.getStatus() == 1)
                .map(SysUser::getId)
                .toList();
        if (enabled.size() != userIds.size()) {
            throw new BusinessException(201, "考勤成员不存在或已停用");
        }

        Long conflicts = memberMapper.selectCount(new LambdaQueryWrapper<AttendanceGroupMember>()
                .in(AttendanceGroupMember::getUserId, userIds)
                .ne(ownGroupId != null, AttendanceGroupMember::getGroupId, ownGroupId));
        if (conflicts != null && conflicts > 0) {
            throw new BusinessException(201, "该用户已加入其他考勤组");
        }

        memberMapper.delete(new LambdaQueryWrapper<AttendanceGroupMember>()
                .eq(AttendanceGroupMember::getGroupId, groupId));
        try {
            for (Long userId : userIds) {
                AttendanceGroupMember member = new AttendanceGroupMember();
                member.setGroupId(groupId);
                member.setUserId(userId);
                memberMapper.insert(member);
            }
        } catch (DuplicateKeyException duplicate) {
            throw new BusinessException(201, "该用户已加入其他考勤组");
        }
    }

    private void ensureShiftExists(Long shiftId) {
        // 班次存在性由本域 shiftMapper 直读（软删行不算存在），不再绕道 service。
        Long count = shiftMapper.selectCount(new LambdaQueryWrapper<AttendanceShift>()
                .eq(AttendanceShift::getId, shiftId));
        if (count == null || count == 0) {
            throw new BusinessException(201, "绑定的班次不存在");
        }
    }

    private String canonicalWorkdays(List<Integer> workdays) {
        if (workdays == null || workdays.isEmpty()) {
            throw new BusinessException(201, "每周工作日不能为空");
        }
        for (Integer day : workdays) {
            if (day == null || day < 1 || day > 7) {
                throw new BusinessException(201, "每周工作日不合法");
            }
        }
        return workdays.stream().distinct().sorted()
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }
}
