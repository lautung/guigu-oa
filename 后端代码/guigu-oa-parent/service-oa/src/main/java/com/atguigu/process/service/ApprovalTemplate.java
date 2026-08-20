package com.atguigu.process.service;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;

/**
 * 审批模板：未发布可写，已发布能发起且只读。调用方不认状态数字。
 */
public interface ApprovalTemplate {

    IPage<ProcessTemplate> findPage(Page<ProcessTemplate> pageParam);

    ProcessTemplate read(Long id);

    void saveUnpublished(ProcessTemplate template);

    void publish(Long id);

    void removeUnpublished(Long id);

    List<ProcessType> findStartable();

    boolean canInitiate(Long id);

    boolean canWrite(Long id);
}
