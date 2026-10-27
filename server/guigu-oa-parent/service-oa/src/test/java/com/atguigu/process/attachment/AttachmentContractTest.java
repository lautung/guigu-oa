package com.atguigu.process.attachment;

import com.atguigu.model.process.Attachment;
import com.atguigu.model.process.Process;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.process.membership.ProcessMembershipExtension;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.jayway.jsonpath.JsonPath;
import tools.jackson.databind.ObjectMapper;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.flowable.engine.TaskService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Proxy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
class AttachmentContractTest {

    private static final String DRAFT_ID = "a9b8ec83-717d-4cf6-8175-3af30680efed";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AttachmentMapper attachmentMapper;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private com.atguigu.process.mapper.OaProcessMapper processMapper;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TestAttachmentObjectStore objectStore;

    @Autowired
    private ObjectMapper objectMapper;

    @BeforeEach
    void prepareProcessDefinition() throws IOException {
        clearEngineInstances();
        objectStore.clear();
        jdbcTemplate.update("DELETE FROM oa_attachment");
        repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("approvalTwoStep")
                .list()
                .forEach(definition -> repositoryService.deleteDeployment(definition.getDeploymentId(), true));
        jdbcTemplate.update("UPDATE oa_process_template SET status = 0 WHERE id = 1");
        ProcessTemplate template = approvalTemplate.read(1L);
        var uploaded = approvalTemplate.uploadProcessDefinition(new MockMultipartFile(
                "file", "approvalTwoStep.zip", "application/zip", approvalTwoStepZip()));
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(template.getId());
    }

    @AfterEach
    void clearLoginThreadLocal() {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
    }

    @Test
    void uploadedDraftCanBeDeletedBeforeStartAndIsLockedAfterBinding() throws Exception {
        String aliceToken = loginToken("alice");
        long pendingId = uploadAndRegister(aliceToken, DRAFT_ID, "receipt.pdf", 31, "application/pdf");

        mockMvc.perform(get("/admin/attachments/drafts/" + DRAFT_ID).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(1));

        mockMvc.perform(delete("/admin/attachments/pending/" + pendingId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/attachments/drafts/" + DRAFT_ID).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        long attachedId = uploadAndRegister(aliceToken, DRAFT_ID, "final.pdf", 53, "application/pdf");
        startWithDraft(aliceToken, DRAFT_ID, attachedId);
        Process process = processMapper.selectList(null).stream()
                .filter(candidate -> Long.valueOf(2L).equals(candidate.getUserId()))
                .findFirst()
                .orElseThrow();

        mockMvc.perform(delete("/admin/attachments/pending/" + attachedId).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));

        mockMvc.perform(get("/admin/attachments/processes/" + process.getId()).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.attachments.length()").value(1))
                .andExpect(jsonPath("$.data.attachments[0].fileName").value("final.pdf"))
                .andExpect(jsonPath("$.data.canUpload").value(true));

        Attachment attached = attachmentMapper.selectById(attachedId);
        assertEquals(Attachment.ATTACHED, attached.getStatus());
        assertTrue(attached.getObjectKey().startsWith("attachments/"));
        assertFalse(objectStore.contains("pending/" + attached.getStorageId()));
        assertTrue(objectStore.contains(attached.getObjectKey()));

        String pendingKey = "pending/" + attached.getStorageId();
        objectStore.put(pendingKey, attached.getSizeBytes(), attached.getContentType());
        attached.setUploadExpiresAt(Date.from(Instant.now().minusSeconds(1)));
        attachmentMapper.updateById(attached);
        attachmentService.cleanupExpiredPending();

        assertFalse(objectStore.contains(pendingKey));
        assertTrue(objectStore.contains(attached.getObjectKey()));
        assertNull(attachmentMapper.selectById(attachedId).getTemporaryObjectKey());
    }

    @Test
    void onlyAnApprovalMemberCanExchangeTheDownloadCredential() throws Exception {
        String aliceToken = loginToken("alice");
        String lisiToken = loginToken("lisi");
        String zhangsanToken = loginToken("zhangsan");
        String adminToken = loginToken("admin");
        long attachmentId = uploadAndRegister(aliceToken, DRAFT_ID, "receipt.pdf", 31, "application/pdf");
        startWithDraft(aliceToken, DRAFT_ID, attachmentId);
        Long processId = processMapper.selectList(null).stream()
                .filter(candidate -> Long.valueOf(2L).equals(candidate.getUserId()))
                .map(Process::getId)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.routers.length()").value(org.hamcrest.Matchers.greaterThan(0)));

        mockMvc.perform(post("/admin/attachments/processes/" + processId + "/" + attachmentId + "/download-url")
                        .header("token", lisiToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(0, objectStore.downloadGrantCount());

        // The configuration-capable seed user is still not a member of this approval.
        mockMvc.perform(post("/admin/attachments/processes/" + processId + "/" + attachmentId + "/download-url")
                        .header("token", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(0, objectStore.downloadGrantCount());

        mockMvc.perform(post("/admin/attachments/processes/" + (processId + 999) + "/" + attachmentId + "/download-url")
                        .header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
        assertEquals(0, objectStore.downloadGrantCount());

        mockMvc.perform(post("/admin/attachments/processes/" + processId + "/" + attachmentId + "/download-url")
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.downloadUrl").isNotEmpty());
        assertEquals(1, objectStore.downloadGrantCount());
        assertEquals(Duration.ofMinutes(15), objectStore.lastDownloadTtl());

        String taskId = taskService.createTaskQuery()
                .processInstanceId(processMapper.selectById(processId).getProcessInstanceId())
                .singleResult()
                .getId();
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", zhangsanToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "processId", processId,
                                "taskId", taskId,
                                "status", 1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/attachments/processes/" + processId + "/" + attachmentId + "/download-url")
                        .header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, objectStore.downloadGrantCount());

        mockMvc.perform(get("/admin/attachments/processes/" + processId).header("token", zhangsanToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.canUpload").value(false));
    }

    @Test
    void readOnlyMembershipCanReadAndDownloadButCannotUpload() throws Exception {
        String aliceToken = loginToken("alice");
        String ccReaderToken = loginToken("cc-reader");
        long attachmentId = uploadAndRegister(aliceToken, DRAFT_ID, "receipt.pdf", 31, "application/pdf");
        startWithDraft(aliceToken, DRAFT_ID, attachmentId);
        Long processId = processMapper.selectList(null).stream()
                .filter(candidate -> Long.valueOf(2L).equals(candidate.getUserId()))
                .map(Process::getId)
                .findFirst()
                .orElseThrow();

        mockMvc.perform(get("/admin/attachments/processes/" + processId).header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.attachments.length()").value(1))
                .andExpect(jsonPath("$.data.canUpload").value(false));

        mockMvc.perform(post("/admin/attachments/processes/" + processId + "/" + attachmentId + "/download-url")
                        .header("token", ccReaderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(Duration.ofMinutes(15), objectStore.lastDownloadTtl());

        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", ccReaderToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "processId", processId,
                                "fileName", "blocked.pdf",
                                "contentType", "application/pdf",
                                "sizeBytes", 31))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    @Test
    void expiredPendingUploadsAreCollectedWithTheirObjectKeys() throws Exception {
        String aliceToken = loginToken("alice");
        String response = mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "draftId", DRAFT_ID,
                                "fileName", "orphan.pdf",
                                "contentType", "application/pdf",
                                "sizeBytes", 31))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long attachmentId = ((Number) JsonPath.read(response, "$.data.attachmentId")).longValue();
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        String pendingKey = attachment.getObjectKey();
        String permanentKey = "attachments/" + attachment.getStorageId();
        objectStore.put(pendingKey, 31, "application/pdf");
        attachment.setCreateTime(Date.from(Instant.now().minus(Duration.ofDays(2))));
        attachmentMapper.updateById(attachment);

        attachmentService.cleanupExpiredPending();

        assertFalse(objectStore.contains(pendingKey));
        assertFalse(objectStore.contains(permanentKey));
        assertNull(attachmentMapper.selectById(attachmentId));
    }

    @Test
    void registrationRejectsAnObjectThatDoesNotMatchItsUploadIntent() throws Exception {
        String aliceToken = loginToken("alice");
        String response = mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", aliceToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "draftId", DRAFT_ID,
                                "fileName", "receipt.pdf",
                                "contentType", "application/pdf",
                                "sizeBytes", 31))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long attachmentId = ((Number) JsonPath.read(response, "$.data.attachmentId")).longValue();
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        objectStore.put(attachment.getObjectKey(), 32, "application/pdf");

        mockMvc.perform(post("/admin/attachments/" + attachmentId + "/complete").header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201));

        assertEquals(Attachment.UPLOADING, attachmentMapper.selectById(attachmentId).getStatus());
    }

    private long uploadAndRegister(
            String token, String draftId, String fileName, long size, String contentType) throws Exception {
        String response = mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "draftId", draftId,
                                "fileName", fileName,
                                "contentType", contentType,
                                "sizeBytes", size))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn()
                .getResponse()
                .getContentAsString();
        long attachmentId = ((Number) JsonPath.read(response, "$.data.attachmentId")).longValue();
        Attachment attachment = attachmentMapper.selectById(attachmentId);
        objectStore.put(attachment.getObjectKey(), size, contentType);

        mockMvc.perform(post("/admin/attachments/" + attachmentId + "/complete").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.fileName").value(fileName));
        return attachmentId;
    }

    private void startWithDraft(String token, String draftId, Long attachmentId) throws Exception {
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "processTemplateId", 1,
                                "processTypeId", 1,
                                "formValues", "{\"formData\":{\"days\":1}}",
                                "attachmentDraftId", draftId,
                                "attachmentIds", java.util.List.of(attachmentId)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String loginToken(String username) throws Exception {
        String loginBody = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(loginBody, "$.data.token");
    }

    private void clearEngineInstances() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "attachment-contract-reset"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
    }

    private static byte[] approvalTwoStepZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream bpmn = AttachmentContractTest.class.getClassLoader()
                     .getResourceAsStream("process/approval-two-step.bpmn20.xml");
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (bpmn == null) throw new IOException("审批合同 BPMN 不存在");
            zip.putNextEntry(new ZipEntry("approval-two-step.bpmn20.xml"));
            bpmn.transferTo(zip);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @TestConfiguration
    static class AttachmentObjectStoreTestConfiguration {
        @Bean
        @Primary
        TestAttachmentObjectStore testAttachmentObjectStore() {
            return new TestAttachmentObjectStore();
        }

        @Bean
        @Primary
        RedisTemplate<String, String> redisTemplate() {
            ConcurrentHashMap<String, String> store = new ConcurrentHashMap<>();
            RedisTemplate<String, String> template = new RedisTemplate<>() {
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
                public void afterPropertiesSet() {
                }
            };
            return template;
        }

        @Bean
        ProcessMembershipExtension readOnlyTestMembership() {
            return (user, process) -> "cc-reader".equals(user.getUsername());
        }
    }

    static class TestAttachmentObjectStore implements AttachmentObjectStore {
        private final Map<String, StoredObject> objects = new ConcurrentHashMap<>();
        private int downloadGrantCount;
        private java.time.Duration lastDownloadTtl;

        @Override
        public String presignUpload(String key, String contentType, java.time.Duration ttl) {
            return "https://objects.test/upload/" + key;
        }

        @Override
        public StoredObject head(String key) {
            StoredObject storedObject = objects.get(key);
            if (storedObject == null) {
                throw new IllegalStateException("Object does not exist");
            }
            return storedObject;
        }

        @Override
        public void copy(String sourceKey, String targetKey) {
            objects.put(targetKey, head(sourceKey));
        }

        @Override
        public void delete(String key) {
            objects.remove(key);
        }

        @Override
        public String presignDownload(String key, String fileName, java.time.Duration ttl) {
            downloadGrantCount++;
            lastDownloadTtl = ttl;
            return "https://objects.test/download/" + key.substring(key.lastIndexOf('/') + 1);
        }

        void put(String key, long size, String contentType) {
            objects.put(key, new StoredObject(size, contentType));
        }

        boolean contains(String key) {
            return objects.containsKey(key);
        }

        int downloadGrantCount() {
            return downloadGrantCount;
        }

        java.time.Duration lastDownloadTtl() {
            return lastDownloadTtl;
        }

        void clear() {
            objects.clear();
            downloadGrantCount = 0;
            lastDownloadTtl = null;
        }
    }
}
