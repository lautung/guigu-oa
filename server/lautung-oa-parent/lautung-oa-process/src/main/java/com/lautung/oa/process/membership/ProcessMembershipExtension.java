package com.lautung.oa.process.membership;

import com.lautung.oa.model.process.Process;
import com.lautung.oa.model.system.SysUser;

/**
 * Extension point for additional read-only members, such as 抄送 recipients.
 */
public interface ProcessMembershipExtension {

    boolean isMember(SysUser user, Process process);
}
