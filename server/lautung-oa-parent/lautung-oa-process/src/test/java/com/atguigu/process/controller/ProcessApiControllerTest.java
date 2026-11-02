package com.atguigu.process.controller;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.process.ApprovalTemplateSupport;
import com.atguigu.process.controller.api.ProcessController;
import com.atguigu.process.service.OaProcessTemplateService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 办事发起页的模板读端点只暴露已发布模板（#131）：未发布模板仍由配置侧按授予读取，
 * 不随发起流程对全部办事用户开放。
 */
class ProcessApiControllerTest {

    private final OaProcessTemplateService templateService = mock(OaProcessTemplateService.class);
    private final ProcessController controller = new ProcessController();

    private ProcessApiControllerTest() {
        ReflectionTestUtils.setField(controller, "processTemplateService", templateService);
    }

    @Test
    void servesPublishedTemplateToTheStartPage() {
        ProcessTemplate published = new ProcessTemplate();
        published.setId(3L);
        published.setStatus(ApprovalTemplateSupport.已发布);
        when(templateService.read(3L)).thenReturn(published);

        assertEquals(published, controller.getProcessTemplate(3L).getData());
    }

    @Test
    void hidesUnpublishedTemplateFromTheStartPage() {
        ProcessTemplate unpublished = new ProcessTemplate();
        unpublished.setId(4L);
        unpublished.setStatus(ApprovalTemplateSupport.未发布);
        when(templateService.read(4L)).thenReturn(unpublished);

        assertNull(controller.getProcessTemplate(4L).getData());
    }

    @Test
    void missingTemplateReadsAsNull() {
        when(templateService.read(99L)).thenReturn(null);

        assertNull(controller.getProcessTemplate(99L).getData());
    }
}
