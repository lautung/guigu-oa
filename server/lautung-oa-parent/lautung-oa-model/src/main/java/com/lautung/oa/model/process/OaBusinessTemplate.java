package com.lautung.oa.model.process;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 业务类型 → 审批模板映射（ADR 0067，#289）：业务类型主键（值域 {@code <域>.<单据>}），
 * 提交时按它解析发起模板。模板与业务类型的关系只有这张表一处；映射行随各域票种子，
 * 本迁移只建表不种行。
 *
 * <p>刻意不继承 {@code BaseEntity}：主键是业务类型字符串，不是自增 id；
 * 公共字段仍由 {@code CommonFieldsMetaObjectHandler} 按字段名填充（ADR 0031）。
 */
@Data
@Schema(description = "业务审批模板映射")
@TableName("oa_business_template")
public class OaBusinessTemplate implements Serializable {

	private static final long serialVersionUID = 1L;

	@Schema(description = "业务类型（值域 <域>.<单据>）")
	@TableId(value = "business_type", type = IdType.INPUT)
	private String businessType;

	@Schema(description = "审批模板 id")
	@TableField("process_template_id")
	private Long processTemplateId;

	@Schema(description = "创建时间（公共字段口径，ADR 0031）")
	@TableField(value = "create_time", fill = FieldFill.INSERT)
	private Date createTime;

	@Schema(description = "更新时间（公共字段口径，ADR 0031）")
	@TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
	private Date updateTime;

	@Schema(description = "逻辑删除标记")
	@TableLogic
	@TableField("is_deleted")
	private Integer isDeleted;
}
