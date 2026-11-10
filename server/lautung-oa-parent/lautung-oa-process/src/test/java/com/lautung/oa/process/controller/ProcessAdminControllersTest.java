package com.lautung.oa.process.controller;

import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.process.ProcessType;
import com.lautung.oa.process.service.OaProcessTemplateService;
import com.lautung.oa.process.service.OaProcessTypeService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.vo.process.ProcessTemplateSaveRequest;
import com.lautung.oa.vo.process.ProcessTemplateUpdateRequest;
import com.lautung.oa.vo.process.ProcessTypeSaveRequest;
import com.lautung.oa.vo.process.ProcessTypeUpdateRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProcessAdminControllersTest {

    private final OaProcessTemplateService templateService = mock(OaProcessTemplateService.class);
    private final OaProcessTypeService typeService = mock(OaProcessTypeService.class);
    private final OaProcessTemplateController templateController = new OaProcessTemplateController();
    private final OaProcessTypeController typeController = new OaProcessTypeController();

    @BeforeEach
    void injectServices() {
        ReflectionTestUtils.setField(templateController, "processTemplateService", templateService);
        ReflectionTestUtils.setField(typeController, "processTypeService", typeService);
    }

    @Test
    void pagesTemplates() {
        when(templateService.findPage(any())).thenReturn(new Page<>(1, 10));

        Result result = templateController.index(1L, 10L);

        assertEquals(200, result.getCode());
    }

    @Test
    void readsSavesUpdatesAndRemovesTemplates() {
        ProcessTemplate template = new ProcessTemplate();
        template.setId(5L);
        when(templateService.read(5L)).thenReturn(template);

        assertEquals(template, templateController.get(5L).getData());

        ProcessTemplateSaveRequest save = new ProcessTemplateSaveRequest();
        save.setName("请假");
        save.setProcessTypeId(2L);
        ProcessTemplateUpdateRequest update = new ProcessTemplateUpdateRequest();
        update.setId(5L);
        update.setProcessDefinitionPath("processes/sha256/abc.zip");

        templateController.save(save);
        templateController.updateById(update);
        templateController.remove(5L);

        ArgumentCaptor<ProcessTemplate> written = ArgumentCaptor.forClass(ProcessTemplate.class);
        verify(templateService, org.mockito.Mockito.times(2)).saveUnpublished(written.capture());
        assertEquals("请假", written.getAllValues().get(0).getName());
        assertEquals(2L, written.getAllValues().get(0).getProcessTypeId());
        assertNull(written.getAllValues().get(0).getId());
        assertEquals(5L, written.getAllValues().get(1).getId());
        verify(templateService).removeUnpublished(5L);
    }

    @Test
    void uploadsAProcessDefinition() throws IOException {
        MultipartFile file = new MockMultipartFile("file", "leave.zip", "application/zip", new byte[] {1});
        when(templateService.uploadProcessDefinition(file))
                .thenReturn(new OaProcessTemplateService.UploadedProcessDefinition(
                        "processes/sha256/abc.zip", "leave", java.util.List.of()));

        Result result = templateController.uploadProcessDefinition(file);

        assertEquals(200, result.getCode());
        assertEquals(
                java.util.Map.of("processDefinitionPath", "processes/sha256/abc.zip",
                        "processDefinitionKey", "leave", "userTasks", java.util.List.of()),
                result.getData());
    }

    @Test
    void reportsRejectedAndFailedUploads() throws IOException {
        MultipartFile rejected = new MockMultipartFile("file", "leave.txt", "text/plain", new byte[] {1});
        when(templateService.uploadProcessDefinition(rejected))
                .thenThrow(new IllegalArgumentException("流程文件必须是 zip"));

        Result rejectedResult = templateController.uploadProcessDefinition(rejected);
        assertEquals(201, rejectedResult.getCode());
        assertEquals("流程文件必须是 zip", rejectedResult.getMessage());

        MultipartFile failed = new MockMultipartFile("file", "leave.zip", "application/zip", new byte[] {1});
        when(templateService.uploadProcessDefinition(failed)).thenThrow(new IOException("disk"));

        assertEquals(201, templateController.uploadProcessDefinition(failed).getCode());
    }

    @Test
    void publishesTemplates() {
        templateController.publish(5L);

        verify(templateService).publish(5L);
    }

    @Test
    void listsPagesAndMutatesProcessTypes() {
        ProcessType type = new ProcessType();
        type.setId(2L);
        when(typeService.list()).thenReturn(List.of(type));
        when(typeService.page(any())).thenReturn(new Page<>(1, 10));
        when(typeService.getById(2L)).thenReturn(type);

        assertEquals(List.of(type), typeController.findAll().getData());
        assertEquals(200, typeController.index(1L, 10L).getCode());
        assertEquals(type, typeController.get(2L).getData());

        ProcessTypeSaveRequest save = new ProcessTypeSaveRequest();
        save.setName("请假类型");
        ProcessTypeUpdateRequest update = new ProcessTypeUpdateRequest();
        update.setId(2L);
        update.setName("请假类型");

        typeController.save(save);
        typeController.updateById(update);
        typeController.remove(2L);

        ArgumentCaptor<ProcessType> written = ArgumentCaptor.forClass(ProcessType.class);
        verify(typeService).save(written.capture());
        assertEquals("请假类型", written.getValue().getName());
        assertNull(written.getValue().getId());
        verify(typeService).updateById(org.mockito.ArgumentMatchers.argThat(
                changed -> changed != null && Long.valueOf(2L).equals(changed.getId())));
        verify(typeService).removeById(2L);
    }
}
