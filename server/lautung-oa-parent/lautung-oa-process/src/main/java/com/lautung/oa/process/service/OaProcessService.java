package com.lautung.oa.process.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.lautung.oa.model.process.Process;

/**
 * 审批账本服务：办事写动作与详情读。列表读走 {@link com.lautung.oa.process.read.ApprovalListReader}（#181）。
 */
public interface OaProcessService extends IService<Process>, Approval {
}
