package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.List;

/**
 * 更新考勤组的请求体（#278，ADR 0034）：只要求主键，其余字段为空即不覆盖；
 * 成员显式列表（含空列表）整体替换、缺省保留（对齐 #249 的 postIds 约定）。
 * 公共字段在 {@link AttendanceGroupRequest}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "更新考勤组")
public class AttendanceGroupUpdateRequest extends AttendanceGroupRequest {

    @NotNull(message = "考勤组标识不能为空")
    @Schema(description = "考勤组id")
    private Long id;

    public AttendanceGroup toEntity() {
        AttendanceGroup group = new AttendanceGroup();
        group.setId(id);
        fill(group);
        return group;
    }

    /** 成员是否在本次请求里显式给出：null=保留现成员。 */
    public boolean hasMemberUserIds() {
        return memberUserIds != null;
    }

    /** 成员集合投影：显式给出时归一为列表（空列表=清空）。 */
    public List<Long> memberUserIdsOrEmpty() {
        return memberUserIds == null ? List.of() : memberUserIds;
    }
}
