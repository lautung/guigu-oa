package com.lautung.oa.model.process;

import com.lautung.oa.model.base.BaseEntity;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
@Schema(description = "ProcessRecord")
@TableName("oa_process_record")
public class ProcessRecord extends BaseEntity {

	private static final long serialVersionUID = 1L;

	@Schema(description = "审批流程id")
	@TableField("process_id")
	private Long processId;

	@Schema(description = "审批描述")
	@TableField("description")
	private String description;

	@Schema(description = "状态")
	@TableField("status")
	private Integer status;

	@Schema(description = "操作用户id")
	@TableField("operate_user_id")
	private Long operateUserId;

	@Schema(description = "操作用户")
	@TableField("operate_user")
	private String operateUser;

	@Schema(description = "动作类型（INITIATE/APPROVE/REJECT/TRANSFER/ADD_SIGN/URGE/WITHDRAW/OTHER，V136 起回填）")
	@TableField("action_type")
	private String actionType;

	@Schema(description = "动作对象用户id（单目标动作；催办等多人集合为空）")
	@TableField("target_user_id")
	private Long targetUserId;

	@Schema(description = "动作对象用户姓名（多人集合顿号连接）")
	@TableField("target_user_name")
	private String targetUserName;

}