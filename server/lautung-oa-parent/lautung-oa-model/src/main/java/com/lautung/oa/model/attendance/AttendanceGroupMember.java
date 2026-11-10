package com.lautung.oa.model.attendance;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 考勤组成员关系（#278，ADR 0073）：纯关系表，硬删。
 *
 * <p>一人最多一组由唯一索引 {@code uk_attendance_member_user} 兜底；移除是物理删除，
 * 等同 {@code sys_user_post} 的整体替换语义，审计走操作日志。不带 {@code is_deleted}，
 * 公共字段只保留 create_time/update_time。
 */
@Data
@Schema(description = "考勤组成员关系")
@TableName("attendance_group_member")
public class AttendanceGroupMember implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    @Schema(description = "考勤组 id")
    @TableField("group_id")
    private Long groupId;

    @Schema(description = "成员用户 id（sys_user.id，全表唯一）")
    @TableField("user_id")
    private Long userId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
