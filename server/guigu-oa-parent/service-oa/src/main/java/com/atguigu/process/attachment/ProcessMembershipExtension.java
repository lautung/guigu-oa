package com.atguigu.process.attachment;

import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;

/**
 * Extension point for additional read-only members, such as future 抄送 recipients.
 */
public interface ProcessMembershipExtension {

    boolean isMember(SysUser user, Process process);
}
