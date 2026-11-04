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

	/** 创建时间范围（yyyy-MM-dd HH:mm:ss 或 yyyy-MM-dd）：保留 main 既有 API 过滤参数。 */
	private String createTimeBegin;
	private String createTimeEnd;

	private Long roleId;
	private Long deptId;

	/** 列表与导出共用的状态筛选：1 正常 / 0 停用；空表示不过滤（#259）。 */
	private Integer status;

}

