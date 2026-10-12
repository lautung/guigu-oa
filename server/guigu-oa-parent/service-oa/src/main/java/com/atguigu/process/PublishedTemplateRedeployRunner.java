package com.atguigu.process;

import com.atguigu.model.process.Process;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 切库后一次性再部署全部已发布审批模板的流程 zip。不改 HTTP，不改模板状态。
 */
@Component
@ConditionalOnProperty(name = "guigu.oa.cutover.redeploy-published", havingValue = "true")
public class PublishedTemplateRedeployRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PublishedTemplateRedeployRunner.class);

    private final OaProcessService processService;
    private final OaProcessTemplateService processTemplateService;

    public PublishedTemplateRedeployRunner(
            OaProcessService processService,
            OaProcessTemplateService processTemplateService
    ) {
        this.processService = processService;
        this.processTemplateService = processTemplateService;
    }

    @Override
    public void run(ApplicationArguments args) {
        ProcessCutoverSupport.requireNoInProgress(
                processService.count(new LambdaQueryWrapper<Process>().eq(Process::getStatus, 1)));
        int count = processTemplateService.redeployPublished();
        log.info("切库再部署已发布审批模板 {} 条", count);
    }
}
