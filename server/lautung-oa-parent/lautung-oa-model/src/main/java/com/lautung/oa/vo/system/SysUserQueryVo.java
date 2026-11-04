//
//
package com.lautung.oa.vo.system;


import lombok.Data;

import java.io.Serializable;

/**
 * <p>
 * 用户查询实体
 * </p>
 */
@Data
public class SysUserQueryVo implements Serializable {
	
	private static final long serialVersionUID = 1L;
	
	private String keyword;

	private String createTimeBegin;
	private String createTimeEnd;

	private Long roleId;
	private Long deptId;

	/** 列表与导出共用的状态筛选：1 正常 / 0 停用；空表示不过滤（#259）。 */
	private Integer status;

}

