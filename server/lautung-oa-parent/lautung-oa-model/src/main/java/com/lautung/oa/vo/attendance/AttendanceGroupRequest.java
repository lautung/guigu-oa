package com.lautung.oa.vo.attendance;

import com.lautung.oa.model.attendance.AttendanceGroup;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 考勤组写请求的公共核心（#278，ADR 0034/0073）：Save/Update 两半一致的字段与落库填充。
 * 名称/班次/工作日的必填性由各壳在自己的 getter 上声明；成员的替换语义是 ADR 0034
 * seam 的两半：save 缺省与空列表都建空组，update 缺省保留、显式列表（含空）整体替换
 * （对齐 #249 的 postIds 约定），成员唯一与启用校验在 service。
 */
@Data
public abstract class AttendanceGroupRequest {

    @Size(max = 50, message = "考勤组名称过长")
    @Schema(description = "考勤组名称")
    private String name;

    @Schema(description = "绑定的班次 id")
    private Long shiftId;

    @Schema(description = "每周工作日（1~7），升序去重后落库")
    private List<Integer> workdays;

    @Schema(description = "成员用户 id 集合；一人最多一组由唯一索引兜底")
    protected List<Long> memberUserIds;

    @Size(max = 255, message = "备注过长")
    @Schema(description = "备注")
    private String remark;

    /** 填充考勤组本体；成员由 service 单独写关系表。 */
    protected void fill(AttendanceGroup group) {
        group.setName(name);
        group.setShiftId(shiftId);
        group.setRemark(remark);
    }
}
