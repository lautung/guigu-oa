package com.atguigu.process.deployment;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.process.ProcessDefinitionStorage;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.repository.Deployment;
import org.flowable.engine.repository.DeploymentBuilder;
import org.flowable.engine.repository.ProcessDefinition;
import org.flowable.engine.repository.ProcessDefinitionQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FlowableProcessDeploymentEngineTest {

    private static final String PATH = "processes/sha256/abc.zip";
    private static final String SHA = "abc";
    private static final String KEY = "leave";

    private final RepositoryService repositoryService = mock(RepositoryService.class);
    private final ProcessDefinitionStorage storage = mock(ProcessDefinitionStorage.class);
    private final ProcessDefinitionQuery definitionQuery = mock(ProcessDefinitionQuery.class);
    private final DeploymentBuilder deploymentBuilder = mock(DeploymentBuilder.class);
    private final Deployment deployment = mock(Deployment.class);
    private final ProcessDefinition definition = mock(ProcessDefinition.class);

    private final FlowableProcessDeploymentEngine engine =
            new FlowableProcessDeploymentEngine(repositoryService, storage);

    @BeforeEach
    void stubFlowableAndStorage() throws IOException {
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
    }

    @Test
    void definitionByIdReturnsNullForBlankIdsWithoutTouchingTheEngine() {
        assertNull(engine.definitionById(null));
        assertNull(engine.definitionById(" "));
        verifyNoInteractions(repositoryService);
    }

    @Test
    void definitionByIdMapsTheFoundDefinitionOrReturnsNullWhenMissing() {
        when(definition.isSuspended()).thenReturn(true);

        ProcessDeploymentEngine.DefinitionSnapshot snapshot = engine.definitionById("def-1");

        assertEquals("def-1", snapshot.id());
        assertEquals(KEY, snapshot.key());
        assertEquals("dep-1", snapshot.deploymentId());
        assertTrue(snapshot.suspended());

        when(definitionQuery.singleResult()).thenReturn(null);
        assertNull(engine.definitionById("def-1"));
    }

    @Test
    void deployVerifiedRejectsUnreadableOrMismatchedArtifacts() throws IOException {
        when(storage.sha256(PATH)).thenThrow(new IOException("missing"));
        assertRejected(201, "流程 zip 不存在或无法读取",
                () -> engine.deployVerified(PATH, KEY, SHA));

        when(storage.sha256(anyString())).thenReturn(SHA);
        when(storage.isContentAddressedPath(PATH, SHA)).thenReturn(false);
        assertRejected(201, "发布流程 zip 内容或路径摘要不匹配",
                () -> engine.deployVerified(PATH, KEY, SHA));

        when(storage.isContentAddressedPath(anyString(), anyString())).thenReturn(true);
        assertRejected(201, "发布流程 zip 内容或路径摘要不匹配",
                () -> engine.deployVerified(PATH, KEY, "different-sha"));
    }

    @Test
    void deployVerifiedRejectsAMissingArtifact() throws IOException {
        when(storage.open(PATH)).thenReturn(null);
        assertRejected(201, "流程 zip 不存在", () -> engine.deployVerified(PATH, KEY, SHA));
    }

    @Test
    void deployVerifiedRejectsAFailedDeploymentRead() throws IOException {
        when(storage.open(PATH)).thenThrow(new IOException("broken"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> engine.deployVerified(PATH, KEY, SHA));
        assertEquals("读取或部署流程 zip 失败", failure.getMessage());
    }

    @Test
    void deployVerifiedRejectsChangedOrUnreadableArtifactsAfterDeployment() throws IOException {
        when(storage.matchesSha256(PATH, SHA)).thenReturn(false);
        assertRejected(201, "发布流程 zip 内容已变化", () -> engine.deployVerified(PATH, KEY, SHA));

        when(storage.matchesSha256(anyString(), anyString())).thenThrow(new IOException("gone"));
        assertRejected(201, "发布流程 zip 无法读取", () -> engine.deployVerified(PATH, KEY, SHA));
    }

    @Test
    void deployVerifiedRejectsWrongDefinitionCounts() {
        when(definitionQuery.list()).thenReturn(List.of());
        assertRejected(201, "流程 zip 无法让指定审批模板发起", () -> engine.deployVerified(PATH, KEY, SHA));

        when(definitionQuery.list()).thenReturn(List.of(definition, definition));
        assertRejected(201, "流程 zip 无法让指定审批模板发起", () -> engine.deployVerified(PATH, KEY, SHA));
    }

    @Test
    void deployVerifiedRejectsASuspendedDefinition() {
        when(definition.isSuspended()).thenReturn(true);
        assertRejected(201, "流程定义当前不可发起", () -> engine.deployVerified(PATH, KEY, SHA));
    }

    @Test
    void deployVerifiedDeploysTheZipByNameAndReturnsTheDeploymentResult() {
        ProcessDeploymentEngine.DeploymentResult result = engine.deployVerified(PATH, KEY, SHA);

        assertEquals("dep-1", result.deploymentId());
        assertEquals("def-1", result.definitionId());
        assertEquals(SHA, result.sha256());
        verify(deploymentBuilder).name(PATH);
        verify(deploymentBuilder).addZipInputStream(any());
        verify(deploymentBuilder).deploy();

        ProcessDeploymentEngine.DeploymentResult withoutExpectedSha =
                engine.deployVerified(PATH, KEY, null);
        assertEquals("dep-1", withoutExpectedSha.deploymentId());
        assertEquals(SHA, withoutExpectedSha.sha256());
    }

    private void assertRejected(int code, String message, Runnable action) {
        GuiguException failure = assertThrows(GuiguException.class, action::run);
        assertEquals(code, failure.getCode().intValue());
        assertEquals(message, failure.getMessage());
    }
}
