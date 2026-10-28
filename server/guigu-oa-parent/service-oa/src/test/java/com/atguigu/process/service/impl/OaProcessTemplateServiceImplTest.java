package com.atguigu.process.service.impl;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessTemplatePublication;
import com.atguigu.model.process.ProcessType;
import com.atguigu.process.ApprovalTemplateSupport;
import com.atguigu.process.ProcessDefinitionStorage;
import com.atguigu.process.mapper.OaProcessTemplateMapper;
import com.atguigu.process.mapper.OaProcessTemplatePublicationMapper;
import com.atguigu.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
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
    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
    private final DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class);
    private final Deployment deployment = mock(Deployment.class);
    private final ProcessDefinition definition = mock(ProcessDefinition.class);

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
        ReflectionTestUtils.setField(service, "repositoryService", repositoryService);

        when(repositoryService.createProcessDefinitionQuery()).thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionId(anyString())).thenReturn(definitionQuery);
        when(definitionQuery.deploymentId(anyString())).thenReturn(definitionQuery);
        when(definitionQuery.processDefinitionKey(anyString())).thenReturn(definitionQuery);
        when(definitionQuery.singleResult()).thenReturn(definition);
        when(definitionQuery.list()).thenReturn(List.of(definition));
        when(repositoryService.createDeployment()).thenReturn(deploymentBuilder);
        when(deploymentBuilder.addZipInputStream(any())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.name(anyString())).thenReturn(deploymentBuilder);
        when(deploymentBuilder.deploy()).thenReturn(deployment);
        when(deployment.getId()).thenReturn("dep-1");
        when(definition.getId()).thenReturn("def-1");
        when(definition.getKey()).thenReturn(KEY);
        when(definition.getDeploymentId()).thenReturn("dep-1");
        when(definition.isSuspended()).thenReturn(false);

        when(storage.sha256(anyString())).thenReturn(SHA);
        when(storage.isContentAddressedPath(anyString(), anyString())).thenReturn(true);
        when(storage.matchesSha256(anyString(), anyString())).thenReturn(true);
        when(storage.open(anyString())).thenReturn(new ByteArrayInputStream(new byte[0]));
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
        verify(repositoryService).createDeployment();
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
    void canInitiateAndCanWriteRequireAStoredTemplate() {
        when(templateMapper.selectById(1L)).thenReturn(null);
        assertFalse(service.canInitiate(1L));
        assertFalse(service.canWrite(1L));

        when(templateMapper.selectById(2L)).thenReturn(template(2L, ApprovalTemplateSupport.已发布));
        when(publicationMapper.selectOne(any())).thenReturn(publication(2L));
        assertTrue(service.canInitiate(2L));
        assertFalse(service.canWrite(2L));

        when(templateMapper.selectById(3L)).thenReturn(template(3L, ApprovalTemplateSupport.未发布));
        assertFalse(service.canInitiate(3L));
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
    void restorePublishedStopsOnBrokenEvidence() {
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template(1L, 1)));
        ProcessTemplatePublication broken = publication(1L);
        broken.setArtifactSha256(" ");
        when(publicationMapper.selectOne(any())).thenReturn(broken);

        assertEquals(201, code(service::restorePublished));
    }

    @Test
    void restorePublishedRejectsMismatchedOrSuspendedDefinitions() {
        ProcessTemplate template = publishable(2L);
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template));
        when(publicationMapper.selectOne(any())).thenReturn(publication(2L));

        when(definition.getKey()).thenReturn("other");
        assertEquals(201, code(service::restorePublished));

        when(definition.getKey()).thenReturn(KEY);
        when(definition.isSuspended()).thenReturn(true);
        assertEquals(201, code(service::restorePublished));
    }

    @Test
    void restorePublishedBackfillsADeploymentIdOrRedeploys() {
        ProcessTemplate template = publishable(3L);
        when(templateMapper.selectPublishedForUpdate()).thenReturn(List.of(template));
        ProcessTemplatePublication missingDeployment = publication(3L);
        missingDeployment.setDeploymentId("");
        when(publicationMapper.selectOne(any())).thenReturn(missingDeployment);

        assertEquals(1, service.restorePublished());
        verify(publicationMapper).updateById(missingDeployment);

        when(definitionQuery.singleResult()).thenReturn(null);
        ProcessTemplatePublication missingDefinition = publication(3L);
        when(publicationMapper.selectOne(any())).thenReturn(missingDefinition);

        assertEquals(1, service.restorePublished());
        verify(publicationMapper, org.mockito.Mockito.times(2))
                .updateById(any(ProcessTemplatePublication.class));
    }

    private int code(org.junit.jupiter.api.function.Executable executable) {
        return assertThrows(GuiguException.class, executable).getCode();
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
