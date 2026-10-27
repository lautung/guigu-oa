package com.atguigu.process.cc;

import com.atguigu.model.system.SysUser;

import java.util.List;

public interface CarbonCopyWriter {

    void copyAtStart(SysUser starter, Long processId, List<Long> recipientIds);

    void copyOnTaskCreated(CarbonCopyTrigger trigger);
}
