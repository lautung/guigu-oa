package com.atguigu.process;

import com.atguigu.common.result.Result;
import com.atguigu.demo.DemoApprovalArtifact;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.process.ProcessType;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.controller.OaProcessTemplateController;
import com.atguigu.process.PublishedTemplateRedeployRunner;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.process.service.OaProcessService;
import com.atguigu.process.service.OaProcessTemplateService;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ProcessFormVo;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.flowable.engine.runtime.ProcessInstance;
import org.flowable.task.api.Task;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.FileSystemException;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "guigu.oa.process-definition-dir=target/test-process-definitions")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
public class ApprovalTemplateContractTest {

    private static final int 审批中 = 1;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private OaProcessTemplateService processTemplateService;

    @Autowired
    private OaProcessTemplateController processTemplateController;

    @Autowired
    private Approval approval;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TaskService taskService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private OaProcessService processService;

    @BeforeEach
    public void deployPublishedTemplateEngineDefinition() throws IOException {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        copyProcessZip("processes/qingjia.zip");
        copyProcessZip("processes/bingjia.zip");
        clearEngineInstances();
        if (repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("approvalTwoStep")
                .count() == 0) {
            repositoryService.createDeployment()
                    .addClasspathResource("process/approval-two-step.bpmn20.xml")
                    .deploy();
        }
    }

    @AfterEach
    public void clearTestState() {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        clearEngineDefinitions("uploadedApproval");
        clearEngineDefinitions("demoApproval");
    }

    @Test
    public void saveUnpublishedCannotInitiateAndStaysWritable() {
        ProcessTemplate saved = save病假申请();

        assertFalse(approvalTemplate.canInitiate(saved.getId()));
        assertTrue(approvalTemplate.canWrite(saved.getId()));
        assertEquals("病假申请", approvalTemplate.read(saved.getId()).getName());
    }

    @Test
    public void saveUnpublishedIgnoresCaller已发布() {
        ProcessTemplate template = 病假申请();
        template.setStatus(1);

        approvalTemplate.saveUnpublished(template);

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertTrue(approvalTemplate.canWrite(template.getId()));
    }

    @Test
    public void findPageIncludes未发布And已发布() {
        ProcessTemplate unpublished = save病假申请();

        IPage<ProcessTemplate> page = approvalTemplate.findPage(new Page<>(1, 20));
        List<Long> ids = page.getRecords().stream().map(ProcessTemplate::getId).toList();

        assertTrue(ids.contains(1L));
        assertTrue(ids.contains(unpublished.getId()));
        assertFalse(approvalTemplate.canInitiate(1L));
        assertEquals("暂不可发起，需核实原审批内容",
                page.getRecords().stream().filter(item -> item.getId().equals(1L)).findFirst().orElseThrow()
                        .getAvailabilityMessage());
        assertTrue(approvalTemplate.canWrite(unpublished.getId()));
        assertEquals("请假", page.getRecords().stream()
                .filter(item -> unpublished.getId().equals(item.getId()))
                .findFirst()
                .orElseThrow()
                .getProcessTypeName());
    }

    @Test
    public void findStartableOnly已发布GroupedBy审批类型() {
        ProcessTemplate unpublished = save病假申请();

        List<ProcessType> groups = approvalTemplate.findStartable();

        assertTrue(groups.isEmpty());

        approvalTemplate.publish(unpublished.getId());

        List<ProcessType> afterPublish = approvalTemplate.findStartable();
        assertTrue(afterPublish.stream().noneMatch(group -> "加班".equals(group.getName())));
        ProcessType leaveAfter = afterPublish.stream()
                .filter(group -> "请假".equals(group.getName()))
                .findFirst()
                .orElseThrow();
        List<Long> idsAfter = leaveAfter.getProcessTemplateList().stream()
                .map(ProcessTemplate::getId)
                .toList();
        assertTrue(idsAfter.contains(unpublished.getId()));
        assertFalse(idsAfter.contains(3L));
        assertFalse(idsAfter.contains(4L));
    }

    @Test
    public void unpublishedCanBeSavedAgainAndDeleted() {
        ProcessTemplate saved = save病假申请();
        saved.setName("病假申请改");
        approvalTemplate.saveUnpublished(saved);

        assertEquals("病假申请改", approvalTemplate.read(saved.getId()).getName());
        assertFalse(approvalTemplate.canInitiate(saved.getId()));
        assertTrue(approvalTemplate.canWrite(saved.getId()));

        approvalTemplate.removeUnpublished(saved.getId());
        assertNull(approvalTemplate.read(saved.getId()));
        assertFalse(approvalTemplate.canWrite(saved.getId()));
        assertFalse(approvalTemplate.canInitiate(saved.getId()));
    }

    @Test
    public void publishMakes未发布已发布AndReadOnly() {
        ProcessTemplate saved = save病假申请();

        approvalTemplate.publish(saved.getId());

        assertTrue(approvalTemplate.canInitiate(saved.getId()));
        assertFalse(approvalTemplate.canWrite(saved.getId()));
        assertEquals("病假申请", approvalTemplate.read(saved.getId()).getName());
    }

    @Test
    public void publishMakes未发布已发布AndStartable() {
        ProcessTemplate saved = save病假申请();
        approvalTemplate.publish(saved.getId());

        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(saved.getId());
        approval.startUp(alice, form);

        assertEquals(1, approval.findStarted(alice, new Page<>(1, 10)).getRecords().size());
        assertEquals(审批中, approval.findStarted(alice, new Page<>(1, 10)).getRecords().get(0).getStatus());
        assertTrue(approvalTemplate.canInitiate(saved.getId()));
        assertFalse(approvalTemplate.canWrite(saved.getId()));
    }

    @Test
    public void demoApprovalArtifactPublishesAndStartsTheDemoFlow() throws Exception {
        ApprovalTemplate.UploadedProcessDefinition uploaded = approvalTemplate.uploadProcessDefinition(
                new MockMultipartFile("file", DemoApprovalArtifact.ARCHIVE_NAME, "application/zip",
                        DemoApprovalArtifact.zip()));
        assertEquals("demoApproval", uploaded.processDefinitionKey());

        ProcessTemplate template = new ProcessTemplate();
        template.setName("演示请假申请");
        template.setProcessTypeId(1L);
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(template.getId());

        assertTrue(approvalTemplate.canInitiate(template.getId()));
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(template.getId());
        approval.startUp(alice, form);

        Task task = taskService.createTaskQuery().taskAssignee("bob").singleResult();
        assertNotNull(task);
        assertEquals("鲍勃审批", task.getName());
    }

    @Test
    public void uploadedFlowZipIsStoredOutsideClasspathAndCanBePublishedFromThatDirectory() throws Exception {
        Path processDirectory = Path.of("target/test-process-definitions").toAbsolutePath();
        Path classpathWriteTarget = Path.of("target/classes/processes/uploadedApproval.zip").toAbsolutePath();
        Files.deleteIfExists(classpathWriteTarget);
        byte[] zip = processDefinitionZip("uploadedApproval");
        MockMultipartFile upload = new MockMultipartFile(
                "file", "../../uploadedApproval.zip", "application/zip", zip);

        // The controller is gated; call it as the seeded configuration admin.
        LoginUserInfoHelper.setUserId(1L);
        LoginUserInfoHelper.setUsername("admin");
        Result result;
        try {
            result = processTemplateController.uploadProcessDefinition(upload);
        } finally {
            LoginUserInfoHelper.removeUserId();
            LoginUserInfoHelper.removeUsername();
        }

        assertEquals(200, result.getCode());
        assertFalse(Files.exists(classpathWriteTarget));
        @SuppressWarnings("unchecked")
        var uploadData = (Map<String, Object>) result.getData();
        String relativePath = (String) uploadData.get("processDefinitionPath");
        assertTrue(relativePath.startsWith("processes/sha256/"));
        assertTrue(relativePath.endsWith(".zip"));
        assertArrayEquals(zip, Files.readAllBytes(processDirectory.resolve(relativePath)));
        ProcessDefinitionStorage recreatedStorage = new ProcessDefinitionStorage(processDirectory);
        try (InputStream storedAfterRestart = recreatedStorage.open(relativePath)) {
            assertNotNull(storedAfterRestart);
            assertArrayEquals(zip, storedAfterRestart.readAllBytes());
        }

        ProcessTemplate template = 病假申请();
        template.setProcessDefinitionKey("uploadedApproval");
        template.setProcessDefinitionPath(relativePath);
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(template.getId());

        assertTrue(approvalTemplate.canInitiate(template.getId()));
        assertEquals(ProcessDefinitionStorage.sha256(processDirectory.resolve(relativePath)),
                jdbcTemplate.queryForObject(
                        "SELECT artifact_sha256 FROM oa_process_template_publication WHERE process_template_id = ?",
                        String.class, template.getId()));
        clearEngineDefinitions("uploadedApproval");
        assertEquals(0L, repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("uploadedApproval")
                .count());
        jdbcTemplate.update("DELETE FROM oa_process_template WHERE id IN (1, 2)");
        assertEquals(1, approvalTemplate.restorePublished());
        assertTrue(repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("uploadedApproval")
                .count() > 0);
        assertTrue(approvalTemplate.canInitiate(template.getId()));
    }

    @Test
    public void processDefinitionPathCannotEscapeConfiguredDirectory() {
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(
                Path.of("target/test-process-definitions"));

        assertThrows(IllegalArgumentException.class, () -> storage.resolve("../outside.zip"));
    }

    @Test
    public void uploadingSameFilenameWithDifferentContentKeepsBothContentAddressedArtifacts() throws Exception {
        Path directory = Path.of("target/test-content-addressed-definitions");
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        byte[] firstBytes = processDefinitionZip("sameName");
        byte[] secondBytes = processDefinitionZip("differentContent");

        var first = storage.store(new MockMultipartFile("file", "sameName.zip", "application/zip", firstBytes));
        var same = storage.store(new MockMultipartFile("file", "sameName.zip", "application/zip", firstBytes));
        var second = storage.store(new MockMultipartFile("file", "sameName.zip", "application/zip", secondBytes));

        assertEquals(first.processDefinitionPath(), same.processDefinitionPath());
        assertNotEquals(first.processDefinitionPath(), second.processDefinitionPath());
        assertArrayEquals(firstBytes, Files.readAllBytes(directory.resolve(first.processDefinitionPath())));
        assertArrayEquals(secondBytes, Files.readAllBytes(directory.resolve(second.processDefinitionPath())));
        assertTrue(storage.matchesSha256(first.processDefinitionPath(), first.sha256()));
    }

    @Test
    public void contentAddressCollisionNeverReplacesExistingBytes() throws Exception {
        Path directory = Path.of("target/test-content-address-collision");
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory);
        Files.createDirectories(directory);
        byte[] requested = processDefinitionZip("collisionKey");
        Path tempArchive = Files.createTempFile(directory, "digest-", ".zip");
        String digest;
        try {
            Files.write(tempArchive, requested);
            digest = ProcessDefinitionStorage.sha256(tempArchive);
        } finally {
            Files.deleteIfExists(tempArchive);
        }
        Path destination = directory.resolve("processes/sha256/" + digest + ".zip");
        Files.createDirectories(destination.getParent());
        byte[] existing = {1, 2, 3};
        Files.write(destination, existing);

        assertThrows(IOException.class, () -> storage.store(
                new MockMultipartFile("file", "collisionKey.zip", "application/zip", requested)));

        assertArrayEquals(existing, Files.readAllBytes(destination));
    }

    @Test
    public void fallsBackToNonReplacingMoveWhenHardLinksAreUnavailable() throws Exception {
        Path directory = Path.of("target/test-hard-link-fallback");
        ProcessDefinitionStorage storage = new ProcessDefinitionStorage(directory, (link, existing) -> {
            throw new FileSystemException(existing.toString(), link.toString(), "Operation not supported");
        });
        byte[] requested = processDefinitionZip("fallbackKey");

        var uploaded = storage.store(new MockMultipartFile(
                "file", "fallbackKey.zip", "application/zip", requested));

        assertArrayEquals(requested, Files.readAllBytes(directory.resolve(uploaded.processDefinitionPath())));
        assertTrue(storage.matchesSha256(uploaded.processDefinitionPath(), uploaded.sha256()));
    }

    @Test
    public void sameBpmnKeyUsesEachTemplatesPinnedDefinitionWhenStartingApproval() throws Exception {
        String key = "sameKeyFrozenContract";
        ProcessTemplate first = saveUploadedTemplate("版本一", key, processDefinitionZip(key, "版本一审批"));
        approvalTemplate.publish(first.getId());
        ApprovalTemplate.PublishedApproval firstPublication = approvalTemplate.resolveForInitiation(first.getId());

        ProcessTemplate second = saveUploadedTemplate("版本二", key, processDefinitionZip(key, "版本二审批"));
        approvalTemplate.publish(second.getId());
        ApprovalTemplate.PublishedApproval secondPublication = approvalTemplate.resolveForInitiation(second.getId());
        assertNotEquals(firstPublication.processDefinitionId(), secondPublication.processDefinitionId());

        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo firstForm = publishedLeaveForm();
        firstForm.setProcessTemplateId(first.getId());
        approval.startUp(alice, firstForm);
        ProcessInstance firstInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(findStartedBusinessKey(alice, first.getId()))
                .singleResult();

        assertEquals(firstPublication.processDefinitionId(), firstInstance.getProcessDefinitionId());
        Task firstTask = taskService.createTaskQuery().processInstanceId(firstInstance.getId()).singleResult();
        assertEquals("版本一审批", firstTask.getName());

        ProcessFormVo secondForm = publishedLeaveForm();
        secondForm.setProcessTemplateId(second.getId());
        approval.startUp(alice, secondForm);
        ProcessInstance secondInstance = runtimeService.createProcessInstanceQuery()
                .processInstanceBusinessKey(findStartedBusinessKey(alice, second.getId()))
                .singleResult();
        assertEquals(secondPublication.processDefinitionId(), secondInstance.getProcessDefinitionId());
        assertEquals("版本二审批", taskService.createTaskQuery()
                .processInstanceId(secondInstance.getId()).singleResult().getName());
    }

    @Test
    public void publicationWriteFailureAfterDeploymentRollsBackDefinitionBindingAndStatus() throws Exception {
        String key = "publishRollbackContract";
        ProcessTemplate template = saveUploadedTemplate(key, key, processDefinitionZip(key, "回滚审批"));
        jdbcTemplate.execute("ALTER TABLE oa_process_template_publication ADD CONSTRAINT fail_publication_sha "
                + "CHECK (artifact_sha256 = '0000000000000000000000000000000000000000000000000000000000000000')");
        try {
            assertThrows(RuntimeException.class, () -> approvalTemplate.publish(template.getId()));
        } finally {
            jdbcTemplate.execute("ALTER TABLE oa_process_template_publication DROP CONSTRAINT IF EXISTS fail_publication_sha");
        }

        assertEquals(0, repositoryService.createProcessDefinitionQuery().processDefinitionKey(key).count());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_template_publication WHERE process_template_id = ?",
                Integer.class, template.getId()));
        assertEquals(0, approvalTemplate.read(template.getId()).getStatus());
        assertTrue(approvalTemplate.canWrite(template.getId()));
    }

    @Test
    public void restoringPublishedTemplatesIsAtomicRetryableAndIdempotent() throws Exception {
        jdbcTemplate.update("DELETE FROM oa_process_template WHERE id IN (1, 2)");
        String firstKey = "restoreFirstContract";
        String secondKey = "restoreSecondContract";
        byte[] firstZip = processDefinitionZip(firstKey, "第一项审批");
        byte[] secondZip = processDefinitionZip(secondKey, "第二项审批");
        ProcessTemplate first = saveUploadedTemplate(firstKey, firstKey, firstZip);
        ProcessTemplate second = saveUploadedTemplate(secondKey, secondKey, secondZip);
        approvalTemplate.publish(first.getId());
        approvalTemplate.publish(second.getId());
        String firstOldDefinitionId = publicationDefinitionId(first.getId());
        String secondOldDefinitionId = publicationDefinitionId(second.getId());
        clearEngineDefinitions(firstKey);
        clearEngineDefinitions(secondKey);

        Path secondArchive = Path.of("target/test-process-definitions")
                .resolve(approvalTemplate.read(second.getId()).getProcessDefinitionPath()).toAbsolutePath();
        Files.write(secondArchive, new byte[]{1, 2, 3, 4});
        assertThrows(RuntimeException.class, () -> approvalTemplate.restorePublished());

        assertEquals(0, repositoryService.createProcessDefinitionQuery().processDefinitionKey(firstKey).count());
        assertEquals(0, repositoryService.createProcessDefinitionQuery().processDefinitionKey(secondKey).count());
        assertEquals(1, approvalTemplate.read(first.getId()).getStatus());
        assertEquals(1, approvalTemplate.read(second.getId()).getStatus());
        assertEquals(firstOldDefinitionId, publicationDefinitionId(first.getId()));
        assertEquals(secondOldDefinitionId, publicationDefinitionId(second.getId()));

        Files.write(secondArchive, secondZip);
        assertEquals(2, approvalTemplate.restorePublished());
        assertTrue(approvalTemplate.canInitiate(first.getId()));
        assertTrue(approvalTemplate.canInitiate(second.getId()));
        assertNotEquals(firstOldDefinitionId, approvalTemplate.resolveForInitiation(first.getId()).processDefinitionId());
        assertNotEquals(secondOldDefinitionId, approvalTemplate.resolveForInitiation(second.getId()).processDefinitionId());
        long definitionCount = repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(firstKey).count()
                + repositoryService.createProcessDefinitionQuery().processDefinitionKey(secondKey).count();
        assertEquals(0, approvalTemplate.restorePublished());
        assertEquals(definitionCount, repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(firstKey).count()
                + repositoryService.createProcessDefinitionQuery().processDefinitionKey(secondKey).count());
    }

    @Test
    public void restoresManuallyVerifiedArtifactWithoutInventingOldEngineIds() throws Exception {
        jdbcTemplate.update("DELETE FROM oa_process_template WHERE id IN (1, 2)");
        String key = "manualRepairContract";
        ProcessTemplate template = saveUploadedTemplate(key, key, processDefinitionZip(key, "人工核实审批"));
        approvalTemplate.publish(template.getId());
        String path = jdbcTemplate.queryForObject(
                "SELECT artifact_path FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, template.getId());
        String sha256 = jdbcTemplate.queryForObject(
                "SELECT artifact_sha256 FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, template.getId());
        clearEngineDefinitions(key);
        jdbcTemplate.update("UPDATE oa_process_template_publication SET deployment_id = NULL, process_definition_id = NULL "
                + "WHERE process_template_id = ?", template.getId());

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertEquals(1, approvalTemplate.restorePublished());
        assertTrue(approvalTemplate.canInitiate(template.getId()));
        assertNotNull(approvalTemplate.resolveForInitiation(template.getId()).processDefinitionId());
        assertEquals(path, jdbcTemplate.queryForObject(
                "SELECT artifact_path FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, template.getId()));
        assertEquals(sha256, jdbcTemplate.queryForObject(
                "SELECT artifact_sha256 FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, template.getId()));
    }

    @Test
    public void replacedPublishedArtifactIsRejectedBeforeApprovalSideEffects() throws Exception {
        jdbcTemplate.update("DELETE FROM oa_process_template WHERE id IN (1, 2)");
        String key = "tamperedStartContract";
        ProcessTemplate template = saveUploadedTemplate(key, key, processDefinitionZip(key, "原始审批"));
        approvalTemplate.publish(template.getId());
        Path archive = Path.of("target/test-process-definitions")
                .resolve(template.getProcessDefinitionPath()).toAbsolutePath();
        Files.write(archive, processDefinitionZip(key, "被替换的审批"));
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(template.getId());
        form.setAttachmentDraftId("9d4ee9ae-a60f-4b07-859d-f9d43d98d261");
        form.setCcUserIds(List.of(5L));

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process_cc", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_attachment WHERE process_id IS NOT NULL", Integer.class));
        assertEquals(0, runtimeService.createProcessInstanceQuery().count());
    }

    @Test
    public void configuredStartupRecoveryRunnerPropagatesMissingLegacyEvidence() {
        assertThrows(RuntimeException.class,
                () -> new PublishedTemplateRedeployRunner(processService, approvalTemplate)
                        .run(new org.springframework.boot.DefaultApplicationArguments()));
        assertEquals(1, approvalTemplate.read(1L).getStatus());
        assertFalse(approvalTemplate.canInitiate(1L));
    }

    @Test
    public void publishRacesWithSaveDeleteAndDuplicatePublishWithoutBreakingFreeze() throws Exception {
        assertCompetingOperationWaitsForPublication("raceSaveContract", "save");
        assertCompetingOperationWaitsForPublication("raceDeleteContract", "delete");
        assertCompetingOperationWaitsForPublication("racePublishContract", "publish");
    }

    @Test
    public void publishFailsWhen流程zipDoesNotMakeStartableKeeps未发布() {
        clearEngineDefinitions("qingjia");
        ProcessTemplate template = 病假申请();
        template.setProcessDefinitionKey("cannotStartFromQingjia");
        template.setProcessDefinitionPath("processes/qingjia.zip");
        approvalTemplate.saveUnpublished(template);

        assertThrows(RuntimeException.class, () -> approvalTemplate.publish(template.getId()));

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertTrue(approvalTemplate.canWrite(template.getId()));
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo unpublishedForm = publishedLeaveForm();
        unpublishedForm.setProcessTemplateId(template.getId());
        assertThrows(RuntimeException.class, () -> approval.startUp(alice, unpublishedForm));
        ProcessFormVo alreadyPublishedQingjia = publishedLeaveForm();
        alreadyPublishedQingjia.setProcessTemplateId(2L);
        assertThrows(RuntimeException.class, () -> approval.startUp(alice, alreadyPublishedQingjia));
        assertTrue(approval.findStarted(alice, new Page<>(1, 10)).getRecords().isEmpty());
    }

    @Test
    public void publishFailsWhen流程zipMissingKeeps未发布() {
        ProcessTemplate template = 病假申请();
        template.setProcessDefinitionPath("processes/missing-no-such.zip");
        approvalTemplate.saveUnpublished(template);

        assertThrows(RuntimeException.class, () -> approvalTemplate.publish(template.getId()));

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertTrue(approvalTemplate.canWrite(template.getId()));
        assertEquals("病假申请", approvalTemplate.read(template.getId()).getName());
    }

    @Test
    public void publishFailsWhen流程zipPathEmptyKeeps未发布() {
        ProcessTemplate template = 病假申请();
        template.setProcessDefinitionPath(null);
        approvalTemplate.saveUnpublished(template);

        assertThrows(RuntimeException.class, () -> approvalTemplate.publish(template.getId()));

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertTrue(approvalTemplate.canWrite(template.getId()));
    }

    @Test
    public void publishFailsWhen流程zipHasNoKeyKeeps未发布() {
        ProcessTemplate template = 病假申请();
        template.setProcessDefinitionKey(null);
        approvalTemplate.saveUnpublished(template);

        assertThrows(RuntimeException.class, () -> approvalTemplate.publish(template.getId()));

        assertFalse(approvalTemplate.canInitiate(template.getId()));
        assertTrue(approvalTemplate.canWrite(template.getId()));
    }

    @Test
    public void 已发布CannotSaveDeleteOrRepublishButCanBeRead() {
        assertEquals("请假申请", approvalTemplate.read(1L).getName());
        assertFalse(approvalTemplate.canInitiate(1L));
        assertEquals("暂不可发起，需核实原审批内容", approvalTemplate.read(1L).getAvailabilityMessage());
        assertFalse(approvalTemplate.canWrite(1L));

        ProcessTemplate change = new ProcessTemplate();
        change.setId(1L);
        change.setName("改掉请假申请");
        change.setProcessTypeId(1L);
        assertThrows(RuntimeException.class, () -> approvalTemplate.saveUnpublished(change));
        assertEquals("请假申请", approvalTemplate.read(1L).getName());

        assertThrows(RuntimeException.class, () -> approvalTemplate.removeUnpublished(1L));
        assertEquals("请假申请", approvalTemplate.read(1L).getName());

        assertThrows(RuntimeException.class, () -> approvalTemplate.publish(1L));
        assertFalse(approvalTemplate.canInitiate(1L));
        assertFalse(approvalTemplate.canWrite(1L));
    }

    @Test
    public void legacyPublishedWithoutEvidenceIsReadOnlyAndRejectedBeforeApprovalSideEffects() {
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(2L);
        form.setAttachmentDraftId("9d4ee9ae-a60f-4b07-859d-f9d43d98d261");
        form.setCcUserIds(List.of(5L));

        assertEquals("暂不可发起，需核实原审批内容", approvalTemplate.read(2L).getAvailabilityMessage());
        assertFalse(approvalTemplate.canInitiate(2L));
        assertTrue(approvalTemplate.findStartable().isEmpty());
        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));

        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process_cc", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_attachment WHERE process_id IS NOT NULL", Integer.class));
        assertEquals(0, runtimeService.createProcessInstanceQuery().count());
    }

    @Test
    public void 按未发布审批模板发起DoesNotLeave审批() {
        assertNull(LoginUserInfoHelper.getUserId());

        ProcessTemplate unpublished = save病假申请();
        SysUser alice = user(2L, "alice", "小艾");
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(unpublished.getId());
        form.setProcessTypeId(1L);
        form.setFormValues("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}");

        assertThrows(RuntimeException.class, () -> approval.startUp(alice, form));
        assertTrue(approval.findStarted(alice, new Page<>(1, 10)).getRecords().isEmpty());
        assertNull(LoginUserInfoHelper.getUserId());
    }

    @Test
    public void 已发布审批模板CanStartApproval() {
        SysUser alice = user(2L, "alice", "小艾");
        ProcessTemplate template = save病假申请();
        approvalTemplate.publish(template.getId());
        ProcessFormVo form = publishedLeaveForm();
        form.setProcessTemplateId(template.getId());

        approval.startUp(alice, form);

        assertEquals(1, approval.findStarted(alice, new Page<>(1, 10)).getRecords().size());
        assertEquals(审批中, approval.findStarted(alice, new Page<>(1, 10)).getRecords().get(0).getStatus());
    }

    private ProcessTemplate save病假申请() {
        ProcessTemplate template = 病假申请();
        try {
            byte[] zip = Files.readAllBytes(Path.of("target/test-process-definitions/processes/bingjia.zip")
                    .toAbsolutePath());
            ApprovalTemplate.UploadedProcessDefinition uploaded = approvalTemplate.uploadProcessDefinition(
                    new MockMultipartFile("file", "bingjia.zip", "application/zip", zip));
            template.setProcessDefinitionKey(uploaded.processDefinitionKey());
            template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        } catch (IOException e) {
            throw new IllegalStateException("测试病假流程 zip 不存在", e);
        }
        approvalTemplate.saveUnpublished(template);
        return template;
    }

    private ProcessTemplate saveUploadedTemplate(String name, String key, byte[] zip) throws IOException {
        ApprovalTemplate.UploadedProcessDefinition uploaded = approvalTemplate.uploadProcessDefinition(
                new MockMultipartFile("file", key + ".zip", "application/zip", zip));
        ProcessTemplate template = new ProcessTemplate();
        template.setName(name);
        template.setProcessTypeId(1L);
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        return template;
    }

    private String publicationDefinitionId(Long templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT process_definition_id FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, templateId);
    }

    private String findStartedBusinessKey(SysUser user, Long templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT CAST(id AS VARCHAR) FROM oa_process WHERE user_id = ? AND process_template_id = ? "
                        + "ORDER BY id DESC LIMIT 1",
                String.class, user.getId(), templateId);
    }

    private void assertCompetingOperationWaitsForPublication(String key, String operation) throws Exception {
        ProcessTemplate template = saveUploadedTemplate(key, key, processDefinitionZip(key, key + " 审批"));
        CountDownLatch publicationReady = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        CountDownLatch competingStarted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        Future<?> publisher = executor.submit(() -> transaction.executeWithoutResult(status -> {
            approvalTemplate.publish(template.getId());
            publicationReady.countDown();
            await(allowCommit);
        }));
        Future<?> competitor;
        try {
            assertTrue(publicationReady.await(10, TimeUnit.SECONDS));
            competitor = executor.submit(() -> {
                competingStarted.countDown();
                if ("save".equals(operation)) {
                    ProcessTemplate change = new ProcessTemplate();
                    change.setId(template.getId());
                    change.setName("发布后迟到保存");
                    approvalTemplate.saveUnpublished(change);
                } else if ("delete".equals(operation)) {
                    approvalTemplate.removeUnpublished(template.getId());
                } else {
                    approvalTemplate.publish(template.getId());
                }
            });
            assertTrue(competingStarted.await(10, TimeUnit.SECONDS));
            Thread.sleep(150);
            assertFalse(competitor.isDone(), operation + " 应等待发布事务提交后的行锁结果");
            allowCommit.countDown();
            publisher.get(10, TimeUnit.SECONDS);
            assertThrows(java.util.concurrent.ExecutionException.class,
                    () -> competitor.get(10, TimeUnit.SECONDS));
        } finally {
            allowCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, repositoryService.createProcessDefinitionQuery().processDefinitionKey(key).count());
        assertEquals(1, approvalTemplate.read(template.getId()).getStatus());
        assertEquals(key, approvalTemplate.read(template.getId()).getName());
        assertFalse(approvalTemplate.canWrite(template.getId()));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("并发合同等待超时");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("并发合同被中断", e);
        }
    }

    private static ProcessTemplate 病假申请() {
        ProcessTemplate template = new ProcessTemplate();
        template.setName("病假申请");
        template.setProcessTypeId(1L);
        template.setProcessDefinitionKey("bingjia");
        template.setProcessDefinitionPath("processes/bingjia.zip");
        template.setDescription("员工病假");
        return template;
    }

    private static byte[] processDefinitionZip(String processDefinitionKey) throws IOException {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                             targetNamespace="http://bpmn.io/schema/bpmn">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(processDefinitionKey);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("approval.bpmn20.xml"));
            zip.write(bpmn.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static byte[] processDefinitionZip(String processDefinitionKey, String taskName) throws IOException {
        String bpmn = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:activiti="http://activiti.org/bpmn"
                             targetNamespace="http://bpmn.io/schema/bpmn">
                  <process id="%s" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="approval" name="%s" activiti:assignee="zhangsan"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approval"/>
                    <sequenceFlow id="flow2" sourceRef="approval" targetRef="end"/>
                  </process>
                </definitions>
                """.formatted(processDefinitionKey, taskName);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("approval.bpmn20.xml"));
            zip.write(bpmn.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void copyProcessZip(String resourcePath) throws IOException {
        Path destination = Path.of("target/test-process-definitions", resourcePath).toAbsolutePath();
        Files.createDirectories(destination.getParent());
        try (InputStream source = ApprovalTemplateContractTest.class.getClassLoader()
                    .getResourceAsStream(resourcePath)) {
            if (source == null) {
                throw new IOException("流程测试 zip 不存在: " + resourcePath);
            }
            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static ProcessFormVo publishedLeaveForm() {
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(1L);
        form.setProcessTypeId(1L);
        form.setFormValues("{\"formData\":{\"days\":1},\"formShowData\":{\"请假天数\":1}}");
        return form;
    }

    private static SysUser user(Long id, String username, String name) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername(username);
        user.setName(name);
        return user;
    }

    private void clearEngineInstances() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "contract-reset"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
    }

    private void clearEngineDefinitions(String processDefinitionKey) {
        repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey(processDefinitionKey)
                .list()
                .forEach(definition -> repositoryService.deleteDeployment(definition.getDeploymentId(), true));
    }

    @TestConfiguration
    static class InMemoryRedisConfig {
        @Bean
        @Primary
        public RedisTemplate<String, String> redisTemplate() {
            ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
            RedisTemplate<String, String> template = new RedisTemplate<String, String>() {
                @Override
                @SuppressWarnings("unchecked")
                public ValueOperations<String, String> opsForValue() {
                    return (ValueOperations<String, String>) Proxy.newProxyInstance(
                            ValueOperations.class.getClassLoader(),
                            new Class<?>[]{ValueOperations.class},
                            (proxy, method, args) -> {
                                if ("set".equals(method.getName()) && args != null && args.length >= 2) {
                                    store.put(String.valueOf(args[0]), String.valueOf(args[1]));
                                    return null;
                                }
                                if ("get".equals(method.getName()) && args != null && args.length >= 1) {
                                    return store.get(args[0]);
                                }
                                if ("toString".equals(method.getName())) {
                                    return "InMemoryValueOperations";
                                }
                                throw new UnsupportedOperationException(method.getName());
                            });
                }

                @Override
                public Boolean delete(String key) {
                    return store.remove(key) != null;
                }

                @Override
                public void afterPropertiesSet() {
                }
            };
            return template;
        }
    }
}
