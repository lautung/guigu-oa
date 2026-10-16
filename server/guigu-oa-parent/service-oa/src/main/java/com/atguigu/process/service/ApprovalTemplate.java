package com.atguigu.process.service;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.io.IOException;
import org.springframework.web.multipart.MultipartFile;

/**
 * 审批模板：未发布可写，已发布能发起且只读。调用方不认状态数字。
 */
public interface ApprovalTemplate {

    IPage<ProcessTemplate> findPage(Page<ProcessTemplate> pageParam);

    ProcessTemplate read(Long id);

    ProcessTemplate findUniquePublishedByProcessDefinitionKey(String processDefinitionKey);

    void saveUnpublished(ProcessTemplate template);

    void publish(Long id);

    void removeUnpublished(Long id);

    List<ProcessType> findStartable();

    boolean canInitiate(Long id);

    boolean canWrite(Long id);

    UploadedProcessDefinition uploadProcessDefinition(MultipartFile file) throws IOException;

    /** Resolves one verified publication for the actual approval start operation. */
    PublishedApproval resolveForInitiation(Long id);

    /** Restores every published template as one transaction; missing evidence fails the whole batch. */
    int restorePublished();

    record UploadedProcessDefinition(String processDefinitionPath, String processDefinitionKey) { }

    record PublishedApproval(ProcessTemplate template, String processDefinitionId) { }
}
