package com.lautung.oa.audit.mapper;

import lombok.Data;

/**
 * 登录日志的写入时快照：只取 {@code sys_user} 的 id 与部门 id，不 join、不读姓名。
 */
@Data
public class LoginUserSnapshot {

    private Long userId;

    private Long deptId;
}
