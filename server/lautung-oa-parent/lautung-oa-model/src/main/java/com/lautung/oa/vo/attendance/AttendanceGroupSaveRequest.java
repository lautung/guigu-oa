package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.ArrayList;
import java.util.List;

/**
 * 新增考勤组的请求体（#278，ADR 0034）：save 只创建、不带主键；
 * 成员缺省/空列表都建空组。公共字段在 {@link AttendanceGroupRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "新增考勤组")
public class AttendanceGroupSaveRequest extends AttendanceGroupRequest {

    @Override
    @NotBlank(message = "考勤组名称不能为空")
    public String getName() {
        return super.getName();
    }

    @Override
    @NotNull(message = "绑定的班次不能为空")
    public Long getShiftId() {
        return super.getShiftId();
    }

    public AttendanceGroup toEntity() {
        AttendanceGroup group = new AttendanceGroup();
        fill(group);
        return group;
    }

    /** 成员集合投影：null 归一为空列表，service 去重校验。 */
    public List<Long> memberUserIdsOrEmpty() {
        return memberUserIds == null ? new ArrayList<>() : memberUserIds;
    }
}
