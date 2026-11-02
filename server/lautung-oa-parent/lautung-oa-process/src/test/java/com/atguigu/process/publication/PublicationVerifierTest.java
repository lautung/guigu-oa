package com.atguigu.process.publication;

import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessTemplatePublication;
import com.atguigu.process.ProcessDefinitionStorage;
import com.atguigu.process.deployment.ProcessDeploymentEngine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 发布判定 module 的理由矩阵（#189，ADR 0003）：真产物存储临时目录 + 手写引擎 port fake，
 * 不落库、不起 Spring。证据问题理由逐字钉住既有六条原文；绑定三态（定义缺失 /
 * 绑定不一致+内部理由 / 可用）与「可用但部署绑定缺失」场景在引擎侧交互上钉住。
 */
class PublicationVerifierTest {

    private static final String KEY = "leave";
    private static final String DEFINITION_ID = "def-1";
    private static final String DEPLOYMENT_ID = "dep-1";

    @TempDir
    Path directory;

    private ProcessDefinitionStorage storage;
    private FakeDeploymentEngine engine;
    private PublicationVerifier verifier;

    private ProcessTemplate template;
    private ProcessTemplatePublication publication;

    @BeforeEach
    void buildVerifierWithRealArtifactStorage() throws IOException {
        storage = new ProcessDefinitionStorage(directory);
        engine = new FakeDeploymentEngine();
        verifier = new PublicationVerifier(storage, engine);

        ProcessDefinitionStorage.UploadedProcessDefinition uploaded =
                storage.store("leave.zip", new byte[] {1, 2, 3});
        engine.definitions.put(DEFINITION_ID,
                new ProcessDeploymentEngine.DefinitionSnapshot(
                        DEFINITION_ID, KEY, DEPLOYMENT_ID, false));

        template = new ProcessTemplate();
        template.setId(9L);
        template.setName("请假模板");
        template.setProcessDefinitionKey(KEY);
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());

        publication = new ProcessTemplatePublication();
        publication.setProcessTemplateId(9L);
        publication.setArtifactPath(uploaded.processDefinitionPath());
        publication.setArtifactSha256(uploaded.sha256());
        publication.setProcessDefinitionKey(KEY);
        publication.setDeploymentId(DEPLOYMENT_ID);
        publication.setProcessDefinitionId(DEFINITION_ID);
    }

    @Test
    void missingPublicationReportsUnreliableBindingWithoutTouchingTheEngine() {
        PublicationVerifier.Verdict verdict = verifier.verify(template, null);

        assertEquals("缺少原发布内容的可靠绑定", verdict.evidenceProblem());
        assertNull(verdict.binding());
        assertFalse(verdict.usable());
        assertEquals(0, engine.definitionQueries);
    }

    @Test
    void evidenceReasonsAreReportedVerbatim() throws IOException {
        assertEquals("缺少发布产物路径", reason(publicationWith(p -> p.setArtifactPath(" "))));
        assertEquals("缺少发布产物校验值", reason(publicationWith(p -> p.setArtifactSha256(""))));
        assertEquals("发布产物路径与校验值不一致",
                reason(publicationWith(p -> p.setArtifactPath("processes/sha256/other.zip"))));
        assertEquals("流程定义 key 与模板记录不一致",
                reason(publicationWith(p -> p.setProcessDefinitionKey("other"))));
        assertEquals("流程定义 key 与模板记录不一致",
                reason(publicationWith(p -> p.setProcessDefinitionKey(" "))));

        ProcessTemplatePublication tampered = publicationWith(p -> { });
        Files.write(storage.resolve(tampered.getArtifactPath()), new byte[] {9, 9, 9},
                StandardOpenOption.TRUNCATE_EXISTING);
        assertEquals("发布流程 zip 缺失或内容已变化", reason(tampered));

        ProcessTemplatePublication missing = publicationWith(p -> { });
        Files.delete(storage.resolve(missing.getArtifactPath()));
        assertEquals("发布流程 zip 缺失或内容已变化", reason(missing));

        // 记录的校验值让内容寻址路径逃出配置目录：路径与校验值字面一致、读取被拒。
        ProcessTemplatePublication escaping = publicationWith(p -> {
            p.setArtifactSha256("../../../outside");
            p.setArtifactPath("processes/sha256/../../../outside.zip");
        });
        assertEquals("发布流程 zip 无法读取", reason(escaping));
    }

    @Test
    void evidenceProblemsShortCircuitBeforeAnyEngineQuery() {
        PublicationVerifier.Verdict verdict = verifier.verify(template,
                publicationWith(p -> p.setArtifactPath("")));

        assertEquals("缺少发布产物路径", verdict.evidenceProblem());
        assertNull(verdict.binding());
        assertFalse(verdict.usable());
        assertEquals(0, engine.definitionQueries);
    }

    @Test
    void blankRecordedDefinitionIdIsMissingWithoutTouchingTheEngine() {
        PublicationVerifier.Verdict verdict = verifier.verify(template,
                publicationWith(p -> p.setProcessDefinitionId("")));

        assertNull(verdict.evidenceProblem());
        assertInstanceOf(PublicationVerifier.Binding.DefinitionMissing.class, verdict.binding());
        assertFalse(verdict.usable());
        assertNull(verdict.definition());
        assertEquals(0, engine.definitionQueries);
    }

    @Test
    void definitionAbsentFromTheEngineIsMissing() {
        engine.definitions.clear();

        PublicationVerifier.Verdict verdict = verifier.verify(template, publication);

        assertNull(verdict.evidenceProblem());
        assertInstanceOf(PublicationVerifier.Binding.DefinitionMissing.class, verdict.binding());
        assertFalse(verdict.usable());
        assertEquals(1, engine.definitionQueries);
    }

    @Test
    void consistentBindingIsUsableAndCarriesTheDefinition() {
        PublicationVerifier.Verdict verdict = verifier.verify(template, publication);

        assertNull(verdict.evidenceProblem());
        PublicationVerifier.Binding.Usable usable =
                assertInstanceOf(PublicationVerifier.Binding.Usable.class, verdict.binding());
        assertTrue(verdict.usable());
        assertEquals(DEFINITION_ID, verdict.definition().id());
        assertEquals(KEY, verdict.definition().key());
        assertEquals(DEPLOYMENT_ID, verdict.definition().deploymentId());
        assertFalse(verdict.definition().suspended());
        assertEquals(DEFINITION_ID, usable.definition().id());
        assertEquals(1, engine.definitionQueries);
    }

    @Test
    void bindingAgainstTheEngineIsReportedInconsistent() {
        engine.definitions.put(DEFINITION_ID,
                new ProcessDeploymentEngine.DefinitionSnapshot(DEFINITION_ID, "other", DEPLOYMENT_ID, false));
        assertInconsistent(verifier.verify(template, publication));

        engine.definitions.put(DEFINITION_ID,
                new ProcessDeploymentEngine.DefinitionSnapshot(DEFINITION_ID, KEY, "dep-2", false));
        assertInconsistent(verifier.verify(template, publication));

        engine.definitions.put(DEFINITION_ID,
                new ProcessDeploymentEngine.DefinitionSnapshot(DEFINITION_ID, KEY, DEPLOYMENT_ID, true));
        assertInconsistent(verifier.verify(template, publication));
    }

    @Test
    void usableEvenWhenTheRecordedDeploymentBindingIsMissing() {
        ProcessTemplatePublication withoutDeployment = publicationWith(p -> p.setDeploymentId(""));

        PublicationVerifier.Verdict verdict = verifier.verify(template, withoutDeployment);

        assertNull(verdict.evidenceProblem());
        assertTrue(verdict.usable());
        assertEquals(DEFINITION_ID, verdict.definition().id());
        assertEquals(DEPLOYMENT_ID, verdict.definition().deploymentId());
        assertFalse(verdict.definition().suspended());
    }

    private void assertInconsistent(PublicationVerifier.Verdict verdict) {
        assertNull(verdict.evidenceProblem());
        PublicationVerifier.Binding.Inconsistent inconsistent =
                assertInstanceOf(PublicationVerifier.Binding.Inconsistent.class, verdict.binding());
        assertEquals("已绑定的流程定义当前不可用", inconsistent.reason());
        assertFalse(verdict.usable());
        assertNull(verdict.definition());
    }

    private String reason(ProcessTemplatePublication broken) {
        return verifier.verify(template, broken).evidenceProblem();
    }

    private ProcessTemplatePublication publicationWith(
            java.util.function.Consumer<ProcessTemplatePublication> change) {
        ProcessTemplatePublication copy = new ProcessTemplatePublication();
        copy.setProcessTemplateId(publication.getProcessTemplateId());
        copy.setArtifactPath(publication.getArtifactPath());
        copy.setArtifactSha256(publication.getArtifactSha256());
        copy.setProcessDefinitionKey(publication.getProcessDefinitionKey());
        copy.setDeploymentId(publication.getDeploymentId());
        copy.setProcessDefinitionId(publication.getProcessDefinitionId());
        change.accept(copy);
        return copy;
    }

    /** 手写引擎 port fake：只记录 definitionById 查询，不支持部署（判定不部署）。 */
    private static final class FakeDeploymentEngine implements ProcessDeploymentEngine {

        private final Map<String, DefinitionSnapshot> definitions = new HashMap<>();
        private int definitionQueries;

        @Override
        public DefinitionSnapshot definitionById(String definitionId) {
            definitionQueries++;
            if (!StringUtils.hasText(definitionId)) return null;
            return definitions.get(definitionId);
        }

        @Override
        public DeploymentResult deployVerified(String path, String key, String expectedSha256) {
            throw new UnsupportedOperationException("发布判定不部署");
        }
    }
}
