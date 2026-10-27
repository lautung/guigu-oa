package com.atguigu.process.membership;

import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;

/**
 * Extension point for additional read-only members, such as 抄送 recipients.
 */
public interface ProcessMembershipExtension {

    boolean isMember(SysUser user, Process process);
}
