package com.atguigu.model.system;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;

import com.atguigu.model.base.BaseEntity;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.ToString;

import java.util.List;

@Data
@Schema(description = "用户")
@TableName("sys_user")
public class SysUser extends BaseEntity {
	
	private static final long serialVersionUID = 1L;

	@Schema(description = "用户名")
	@TableField("username")
	private String username;

	@ToString.Exclude
	@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
	@Schema(description = "密码")
	@TableField("password")
	private String password;

	@Schema(description = "姓名")
	@TableField("name")
	private String name;

	@Schema(description = "手机")
	@TableField("phone")
	private String phone;

	@Schema(description = "头像地址")
	@TableField("head_url")
	private String headUrl;

	@Schema(description = "部门id")
	@TableField("dept_id")
	private Long deptId;

	@Schema(description = "岗位id")
	@TableField("post_id")
	private Long postId;

	@Schema(description = "描述")
	@TableField("description")
	private String description;

	@Schema(description = "openId")
	@TableField("open_id")
	private String openId;

	@Schema(description = "状态（1：正常 0：停用）")
	@TableField("status")
	private Integer status;

	@JsonIgnore
	@Schema(description = "令牌版本；改密递增，旧版本的令牌不再鉴权", hidden = true)
	// Only the password-change statement writes this column; a general entity update must not
	// bring back a stale value read before a concurrent change.
	@TableField(value = "token_version", updateStrategy = FieldStrategy.NEVER)
	private Long tokenVersion;

	/** Tokens must carry this version to authenticate; rows from before the column read as zero. */
	public long tokenVersionOrDefault() {
		return tokenVersion == null ? 0L : tokenVersion;
	}

	/** Whether a token carrying this version still matches the row's credentials. */
	public boolean acceptsTokenVersion(long tokenVersion) {
		return tokenVersion == tokenVersionOrDefault();
	}

	@TableField(exist = false)
	private List<SysRole> roleList;
	//岗位
	@TableField(exist = false)
	private String postName;
	//部门
	@TableField(exist = false)
	private String deptName;
}

