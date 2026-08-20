package com.atguigu.process.service;

import com.atguigu.model.process.ProcessTemplate;
import com.baomidou.mybatisplus.spring.service.IService;

/**
 * <p>
 * 审批模板 服务类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
public interface OaProcessTemplateService extends IService<ProcessTemplate>, ApprovalTemplate {

    /**
     * 切库后再部署全部已发布审批模板的流程 zip。不改模板状态。
     */
    int redeployPublished();
}
