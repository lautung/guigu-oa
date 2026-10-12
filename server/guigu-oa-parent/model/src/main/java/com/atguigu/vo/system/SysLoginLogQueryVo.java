package com.atguigu.vo.system;

import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
public class SysLoginLogQueryVo {
	
	@Schema(description = "用户账号")
	private String username;

	private String createTimeBegin;
	private String createTimeEnd;

}

