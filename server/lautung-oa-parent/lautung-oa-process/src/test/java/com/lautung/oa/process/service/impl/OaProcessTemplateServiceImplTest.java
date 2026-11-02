package com.lautung.oa.process.service.impl;

import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.process.ProcessTemplate;
import com.lautung.oa.model.process.ProcessTemplatePublication;
import com.lautung.oa.model.process.ProcessType;
import com.lautung.oa.process.ApprovalTemplateSupport;
import com.lautung.oa.process.ProcessDefinitionStorage;
import com.lautung.oa.process.deployment.ProcessDeploymentEngine;
import com.lautung.oa.process.mapper.OaProcessTemplateMapper;
import com.lautung.oa.process.publication.PublicationVerifier;
import com.lautung.oa.process.mapper.OaProcessTemplatePublicationMapper;
import com.lautung.oa.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OaProcessTemplateServiceImplTest {

    private static final String PATH = "processes/sha256/abc.zip";
    private static final String SHA = "abc";
    private static final String KEY = "leave";

    private final OaProcessTemplateMapper templateMapper = mock(OaProcessTemplateMapper.class);
    private final OaProcessTypeService typeService = mock(OaProcessTypeService.class);
    private final OaProcessTemplatePublicationMapper publicationMapper =
            mock(OaProcessTemplatePublicationMapper.class);
    private final ProcessDefinitionStorage storage = mock(ProcessDefinitionStorage.class);
    private final ProcessDeploymentEngine deploymentEngine = mock(ProcessDeploymentEngine.class);

    private final OaProcessTemplateServiceImpl service = new OaProcessTemplateServiceImpl();

    @BeforeAll
    static void registerTableMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, ProcessTemplate.class);
        TableInfoHelper.initTableInfo(assistant, ProcessTemplatePublication.class);
    }

    @BeforeEach
    void injectCollaborators() throws IOException {
        ReflectionTestUtils.setField(service, "baseMapper", templateMapper);
        ReflectionTestUtils.setField(service, "processTypeService", typeService);
        ReflectionTestUtils.setField(service, "publicationMapper", publicationMapper);
        ReflectionTestUtils.setField(service, "processDefinitionStorage", storage);
        ReflectionTestUtils.setField(service, "deploymentEngine", deploymentEngine);
        ReflectionTestUtils.setField(service, "publicationVerifier",
                new PublicationVerifier(storage, deploymentEngine));

        when(deploymentEngine.definitionById(anyString())).thenReturn(
                new ProcessDeploymentEngine.DefinitionSnapshot("def-1", KEY, "dep-1", false));
        when(deploymentEngine.deployVerified(anyString(), anyString(), anyString())).thenReturn(
                new ProcessDeploymentEngine.DeploymentResult("dep-1", "def-1", SHA));

        when(storage.sha256(anyString())).thenReturn(SHA);
        when(storage.isContentAddressedPath(anyString(), anyString())).thenReturn(true);
        when(storage.matchesSha256(anyString(), anyString())).thenReturn(true);
        when(publicationMapper.insert(any(ProcessTemplatePublication.class))).thenReturn(1);
        when(publicationMapper.updateById(any(ProcessTemplatePublication.class))).thenReturn(1);
    }

    @Test
    void findPageDecoratesTypeNamesAndAvailability() {
        ProcessTemplate typed = template(1L, ApprovalTemplateSupport.已发布);
        typed.setProcessTypeId(2L);
        ProcessTemplate untyped = template(2L, ApprovalTemplateSupport.未发布);
        Page<ProcessTemplate> page = new Page<>(1, 10);
        page.setRecords(List.of(typed, untyped));
        ProcessType type = new ProcessType();
        type.setName("请假");
        when(templateMapper.selectPage(any(), isNull())).thenReturn(page);
        when(typeService.getById(2L)).thenReturn(type);

        Page<?> result = (Page<?>) service.findPage(new Page<>(1, 10));

        assertEquals(2, result.getRecords().size());
        assertEquals("请假", typed.getProcessTypeName());
        assertFalse(typed.getAvailableForInitiation());
        assertEquals("暂不可发起，需核实原审批内容", typed.getAvailabilityMessage());
        assertNull(untyped.getProcessTypeName());
        assertNull(untyped.getAvailabilityMessage());
    }

    @Test
    void readHandlesMissingTemplatesAndMissingTypes() {
        assertNull(service.read(9L));

        ProcessTemplate template = template(1L, ApprovalTemplateSupport.未发布);
        template.setProcessTypeId(2L);
        when(templateMapper.selectById(1L)).thenReturn(template);
        when(typeService.getById(2L)).thenReturn(null);

        assertSame(template, service.read(1L));
        assertNull(template.getProcessTypeName());
    }

    @Test
    void findUniquePublishedRequiresExactlyOneMatch() {
        when(templateMapper.selectList(any())).thenReturn(List.of());
        assertNull(service.findUniquePublishedByProcessDefinitionKey(KEY));

        when(templateMapper.selectList(any())).thenReturn(List.of(template(1L, 1), template(2L, 1)));
        assertNull(service.findUniquePublishedByProcessDefinitionKey(KEY));

        ProcessTemplate unique = template(3L, ApprovalTemplateSupport.已发布);
        when(templateMapper.selectList(any())).thenReturn(List.of(unique));
        assertSame(unique, service.findUniquePublishedByProcessDefinitionKey(KEY));
    }

    @Test
    void saveUnpublishedInsertsNewAndGuardsExistingTemplates() {
        ProcessTemplate fresh = template(null, null);
        service.saveUnpublished(fresh);
        assertEquals(ApprovalTemplateSupport.未发布, fresh.getStatus());
        verify(templateMapper).insert(fresh);

        ProcessTemplate missing = template(9L, null);
        when(templateMapper.selectByIdForUpdate(9L)).thenReturn(null);
        assertEquals(201, code(() -> service.saveUnpublished(missing)));

        ProcessTemplate published = template(2L, ApprovalTemplateSupport.已发布);
        when(templateMapper.selectByIdForUpdate(2L)).thenReturn(template(2L, 1));
        assertEquals(201, code(() -> service.saveUnpublished(published)));

        ProcessTemplate editable = template(3L, ApprovalTemplateSupport.已发布);
        when(templateMapper.selectByIdForUpdate(3L)).thenReturn(template(3L, 0));
        service.saveUnpublished(editable);
        assertEquals(ApprovalTemplateSupport.未发布, editable.getStatus());
        verify(templateMapper).updateById(editable);
    }

    @Test
    void publishRejectsMissingPublishedAndIncompleteTemplates() throws IOException {
        when(templateMapper.selectByIdForUpdate(1L)).thenReturn(null);
        assertEquals(201, code(() -> service.publish(1L)));

        when(templateMapper.selectByIdForUpdate(2L)).thenReturn(template(2L, 1));
        assertEquals(201, code(() -> service.publish(2L)));

        ProcessTemplate blankPath = template(3L, 0);
        blankPath.setProcessDefinitionPath(" ");
        when(templateMapper.selectByIdForUpdate(3L)).thenReturn(blankPath);
        assertEquals(201, code(() -> service.publish(3L)));

        ProcessTemplate unreadable = publishable(4L);
        when(templateMapper.selectByIdForUpdate(4L)).thenReturn(unreadable);
        when(storage.sha256(PATH)).thenThrow(new IOException("missing"));
        assertEquals(201, code(() -> service.publish(4L)));

        ProcessTemplate wrongPath = publishable(5L);
        when(templateMapper.selectByIdForUpdate(5L)).thenReturn(wrongPath);
        when(storage.isContentAddressedPath(PATH, SHA)).thenReturn(false);
        assertEquals(201, code(() -> service.publish(5L)));
    }

    @Test
    void publishDeploysAndRecordsThePublication() {
        ProcessTemplate template = publishable(6L);
        when(templateMapper.selectByIdForUpdate(6L)).thenReturn(template);
        when(templateMapper.updateById(template)).thenReturn(1);

        service.publish(6L);

        assertEquals(ApprovalTemplateSupport.已发布, template.getStatus());
        verify(publicationMapper).insert(any(ProcessTemplatePublication.class));
        verify(deploymentEngine).deployVerified(PATH, KEY, SHA);
    }

    @Test
    void publishFailsWhenTheStatusWriteIsRejected() {
        ProcessTemplate template = publishable(6L);
        when(templateMapper.selectByIdForUpdate(6L)).thenReturn(template);
        when(templateMapper.updateById(template)).thenReturn(0);

        assertEquals(201, code(() -> service.publish(6L)));
    }

    @Test
    void removeUnpublishedHandlesEveryStatus() {
        service.removeUnpublished(1L); // missing template is a no-op

        when(templateMapper.selectByIdForUpdate(2L)).thenReturn(template(2L, 1));
        assertEquals(201, code(() -> service.removeUnpublished(2L)));

        when(templateMapper.selectByIdForUpdate(3L)).thenReturn(template(3L, 0));
        service.removeUnpublished(3L);
        verify(templateMapper).deleteById(3L);
    }

    @Test
    void findStartableGroupsAvailableTemplatesByType() {
        ProcessTemplate typed = publishable(1L);
        typed.setStatus(ApprovalTemplateSupport.已发布);
        ProcessTemplate untyped = publishable(2L);
        untyped.setStatus(ApprovalTemplateSupport.已发布);
        untyped.setProcessTypeId(null);
        when(templateMapper.selectList(any())).thenReturn(List.of(typed, untyped));
        when(publicationMapper.selectOne(any())).thenReturn(publication(1L));
        ProcessType type = new ProcessType();
        type.setId(2L);
        when(typeService.getById(2L)).thenReturn(type);

        List<ProcessType> groups = service.findStartable();

        assertEquals(1, groups.size());
        assertEquals(1, groups.get(0).getProcessTemplateList().size());
    }

    @Test
    void canWriteRequiresAStoredTemplate() {
        when(templateMapper.selectById(1L)).thenReturn(null);
        assertFalse(service.canWrite(1L));

        when(templateMapper.selectById(2L)).thenReturn(template(2L, ApprovalTemplateSupport.已发布));
        assertFalse(service.canWrite(2L));

        when(templateMapper.selectById(3L)).thenReturn(template(3L, ApprovalTemplateSupport.未发布));
        assertTrue(service.canWrite(3L));
    }

    @Test
    void uploadProcessDefinitionDelegatesToStorage() throws IOException {
        when(storage.store(any())).thenReturn(
                new ProcessDefinitionStorage.UploadedProcessDefinition(PATH, KEY, SHA));

        OaProcessTemplateServiceImpl.UploadedProcessDefinition uploaded =
                service.uploadProcessDefinition(new MockMultipartFile("file", "leave.zip",
                        "application/zip", new byte[] {1}));

        assertEquals(PATH, uploaded.processDefinitionPath());
        assertEquals(KEY, uploaded.processDefinitionKey());
    }

    @Test
    void resolveForInitiationRejectsUnpublishedAndUnverifiableTemplates() {
        when(templateMapper.selectById(1L)).thenReturn(null);
        assertEquals(201, code(() -> service.resolveForInitiation(1L)));

        when(templateMapper.selectById(2L)).thenReturn(template(2L, ApprovalTemplateSupport.未发布));
        assertEquals(201, code(() -> service.resolveForInitiation(2L)));

        when(templateMapper.selectById(3L)).thenReturn(template(3L, ApprovalTemplateSupport.已发布));
        when(publicationMapper.selectOne(any())).thenReturn(null);
        assertEquals(201, code(() -> service.resolveForInitiation(3L)));

        ProcessTemplate template = publishable(4L);
        template.setStatus(ApprovalTemplateSupport.已发布);
        when(templateMapper.selectById(4L)).thenReturn(template);
        ProcessTemplatePublication noPath = publication(4L);
        noPath.setArtifactPath("");
        when(publicationMapper.selectOne(any())).thenReturn(noPath);
        assertEquals(201, code(() -> service.resolveForInitiation(4L)));

        ProcessTemplatePublication noDefinition = publication(4L);
        noDefinition.setProcessDefinitionId("");
        when(publicationMapper.selectOne(any())).thenReturn(noDefinition);
        assertEquals(201, code(() -> service.resolveForInitiation(4L)));
    }

    @Test
    void resolveForInitiationReturnsTheVerifiedDefinition() {
        ProcessTemplate template = publishable(5L);
        template.setStatus(ApprovalTemplateSupport.已发布);
        when(templateMapper.selectById(5L)).thenReturn(template);
        when(publicationMapper.selectOne(any())).thenReturn(publication(5L));

        OaProcessTemplateServiceImpl.PublishedApproval approval = service.resolveForInitiation(5L);

        assertSame(template, approval.template());
        assertEquals("def-1", approval.processDefinitionId());
    }

    @Test
    void resolveForInitiationRejectsIncompleteDeploymentBinding() {
        ProcessTemplate template = publishable(7L);
        template.setStatus(ApprovalTemplateSupport.已发布);
        when(templateMapper.selectById(7L)).thenReturn(template);
        ProcessTemplatePublication missingDeployment = publication(7L);
        missingDeployment.setDeploymentId("");
        when(publicationMapper.selectOne(any())).thenReturn(missingDeployment);

        // 发布即写入部署绑定；缺失说明记录不完整，恢复补齐前不可发起（行为保持）。
        assertEquals(201, code(() -> service.resolveForInitiation(7L)));
    }

    @Test
    void findPageMarksPublishedTemplatesWithCompletePublicationAvailable() {
        ProcessTemplate published = publishable(8L);
        published.setStatus(ApprovalTemplateSupport.已发布);
        Page<ProcessTemplate> page = new Page<>(1, 10);
        page.setRecords(List.of(published));
        when(templateMapper.selectPage(any(), isNull())).thenReturn(page);
        when(publicationMapper.selectOne(any())).thenReturn(publication(8L));

        service.findPage(new Page<>(1, 10));

        assertTrue(published.getAvailableForInitiation());
        assertNull(published.getAvailabilityMessage());
    }

    @Test
    void restorePublishedStopsOnBrokenEvidence() {
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template(1L, 1)));
        ProcessTemplatePublication broken = publication(1L);
        broken.setArtifactSha256(" ");
        when(publicationMapper.selectOne(any())).thenReturn(broken);

        BusinessException failure = assertThrows(BusinessException.class, service::restorePublished);

        assertEquals(201, failure.getCode());
        // 证据问题分支：停止文案逐字，「切库恢复停止：…（理由）」内嵌结构保留。
        assertEquals("切库恢复停止：审批模板 1 暂不可发起，需核实原审批内容（缺少发布产物校验值）",
                failure.getMessage());
        verify(deploymentEngine, never()).deployVerified(anyString(), anyString(), anyString());
    }

    @Test
    void restorePublishedRejectsMismatchedOrSuspendedDefinitions() {
        ProcessTemplate template = publishable(2L);
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template));
        when(publicationMapper.selectOne(any())).thenReturn(publication(2L));

        when(deploymentEngine.definitionById("def-1")).thenReturn(
                new ProcessDeploymentEngine.DefinitionSnapshot("def-1", "other", "dep-1", false));
        BusinessException mismatched = assertThrows(BusinessException.class, service::restorePublished);
        assertEquals(201, mismatched.getCode());
        // 绑定不一致分支：停止文案逐字。
        assertEquals("切库恢复停止：审批模板发布绑定与流程定义不一致", mismatched.getMessage());

        when(deploymentEngine.definitionById("def-1")).thenReturn(
                new ProcessDeploymentEngine.DefinitionSnapshot("def-1", KEY, "dep-1", true));
        assertEquals(201, code(service::restorePublished));
    }

    @Test
    void restorePublishedBackfillsTheDeploymentIdFromTheUsableVerdict() {
        ProcessTemplate template = publishable(3L);
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template));
        ProcessTemplatePublication missingDeployment = publication(3L);
        missingDeployment.setDeploymentId("");
        when(publicationMapper.selectOne(any())).thenReturn(missingDeployment);

        assertEquals(1, service.restorePublished());

        // 可用且部署绑定空：按记录 definitionId 查得的引擎定义回填部署绑定（可靠依据非猜测）。
        assertEquals("dep-1", missingDeployment.getDeploymentId());
        assertEquals("def-1", missingDeployment.getProcessDefinitionId());
        verify(publicationMapper).updateById(missingDeployment);
        verify(deploymentEngine, never()).deployVerified(anyString(), anyString(), anyString());
        // 编排不自拼比对：一次 verify 一次引擎查询，不再重复查。
        verify(deploymentEngine, times(1)).definitionById(anyString());
    }

    @Test
    void restorePublishedRedeploysThroughThePortWhenTheDefinitionIsMissing() {
        ProcessTemplate template = publishable(4L);
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template));
        ProcessTemplatePublication missingDefinition = publication(4L);
        when(publicationMapper.selectOne(any())).thenReturn(missingDefinition);
        when(deploymentEngine.definitionById("def-1")).thenReturn(null);
        when(deploymentEngine.deployVerified(anyString(), anyString(), anyString())).thenReturn(
                new ProcessDeploymentEngine.DeploymentResult("dep-2", "def-2", SHA));

        assertEquals(1, service.restorePublished());

        // 定义缺失：经 port 重部署，重取的部署与定义 id 落库。
        verify(deploymentEngine).deployVerified(PATH, KEY, SHA);
        assertEquals("dep-2", missingDefinition.getDeploymentId());
        assertEquals("def-2", missingDefinition.getProcessDefinitionId());
        verify(publicationMapper).updateById(missingDefinition);
        verify(deploymentEngine, times(1)).definitionById(anyString());
    }

    @Test
    void restorePublishedLeavesCompleteUsableBindingsUntouched() {
        ProcessTemplate template = publishable(5L);
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template));
        when(publicationMapper.selectOne(any())).thenReturn(publication(5L));

        assertEquals(0, service.restorePublished());

        verify(publicationMapper, never()).updateById(any(ProcessTemplatePublication.class));
        verify(deploymentEngine, never()).deployVerified(anyString(), anyString(), anyString());
        verify(deploymentEngine, times(1)).definitionById("def-1");
    }

    private int code(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(BusinessException.class, executable).getCode();
    }

    private ProcessTemplate template(Long id, Integer status) {
        ProcessTemplate template = new ProcessTemplate();
        template.setId(id);
        template.setStatus(status);
        template.setName("请假模板");
        template.setProcessDefinitionKey(KEY);
        return template;
    }

    private ProcessTemplate publishable(Long id) {
        ProcessTemplate template = template(id, ApprovalTemplateSupport.未发布);
        template.setProcessTypeId(2L);
        template.setProcessDefinitionPath(PATH);
        return template;
    }

    private ProcessTemplatePublication publication(Long templateId) {
        ProcessTemplatePublication publication = new ProcessTemplatePublication();
        publication.setProcessTemplateId(templateId);
        publication.setArtifactPath(PATH);
        publication.setArtifactSha256(SHA);
        publication.setProcessDefinitionKey(KEY);
        publication.setDeploymentId("dep-1");
        publication.setProcessDefinitionId("def-1");
        return publication;
    }
}
