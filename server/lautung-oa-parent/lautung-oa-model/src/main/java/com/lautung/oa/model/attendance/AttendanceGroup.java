package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 考勤组（#278，ADR 0073）：绑定一个班次 + 每周工作日多选。
 *
 * <p>成员在 {@link AttendanceGroupMember}，一人最多一组；成员直接选人，
 * 不按部门圈人。workdays 是升序去重后的星期值逗号串，如 {@code 1,2,3,4,5}。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(description = "考勤组")
@TableName("attendance_group")
public class AttendanceGroup extends BaseEntity {

    private static final long serialVersionUID = 1L;

    @Schema(description = "考勤组名称")
    @TableField("name")
    private String name;

    @Schema(description = "绑定的班次 id")
    @TableField("shift_id")
    private Long shiftId;

    @Schema(description = "每周工作日，如 1,2,3,4,5")
    @TableField("workdays")
    private String workdays;

    @Schema(description = "备注")
    @TableField("remark")
    private String remark;
}
