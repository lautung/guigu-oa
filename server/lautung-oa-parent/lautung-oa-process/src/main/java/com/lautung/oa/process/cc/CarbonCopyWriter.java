package com.lautung.oa.process.cc;

import com.lautung.oa.model.system.SysUser;

import java.util.List;

public interface CarbonCopyWriter {

    void copyAtStart(SysUser starter, Long processId, List<Long> recipientIds);

    void copyOnTaskCreated(CarbonCopyTrigger trigger);
}
