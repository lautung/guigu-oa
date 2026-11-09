package com.lautung.oa.process.approval;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 审批人规则服务端面（ADR 0065/0069，#285）：上传解析 userTask 清单、规则 CRUD、
 * 发布合成 oa-approvers.json sidecar（证据指向合成件）与发布校验 201。
 * 已发布规则随产物冻结不可再改；解析不出 BPMN 的 zip 可保存、发布拒绝。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "lautung.oa.process-definition-dir=target/test-process-definitions")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql", "/approver-contract-schema.sql"})
class ApproverRuleContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private com.lautung.oa.common.jwt.JwtHelper jwtHelper;

    @Autowired
    private com.lautung.oa.process.service.ApprovalTemplate approvalTemplate;

    @org.springframework.boot.test.context.TestConfiguration
    static class InMemoryRedisConfig {
        @org.springframework.context.annotation.Bean
        @org.springframework.context.annotation.Primary
        public org.springframework.data.redis.core.RedisTemplate<String, String> redisTemplate() {
            return new com.lautung.oa.support.InMemoryRedisTemplate();
        }
    }

    @AfterEach
    void removeContractTemplates() {
        jdbcTemplate.update("DELETE FROM oa_process_template_publication WHERE process_template_id >= 800");
        jdbcTemplate.update("DELETE FROM oa_process_approver_rule WHERE process_template_id >= 800");
        jdbcTemplate.update("DELETE FROM oa_process_template WHERE id >= 800");
    }

    @Test
    void uploadReturnsBpmnUserTasksForTheConfigPage() throws Exception {
        mockMvc.perform(multipart("/admin/process/processTemplate/uploadProcessDefinition")
                        .file(new MockMultipartFile("file", "approverRuleFlow.zip", "application/zip", flowZip()))
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.processDefinitionKey").value("approverRuleFlow"))
                .andExpect(jsonPath("$.data.userTasks", hasSize(2)))
                .andExpect(jsonPath("$.data.userTasks[0].key").value("first"))
                .andExpect(jsonPath("$.data.userTasks[0].name").value("一审"))
                .andExpect(jsonPath("$.data.userTasks[0].assignee").doesNotExist())
                .andExpect(jsonPath("$.data.userTasks[1].key").value("second"))
                .andExpect(jsonPath("$.data.userTasks[1].name").value("二审"));
    }

    @Test
    void uploadOfZipWithoutParsableBpmnStillSavesWithEmptyNodeList() throws Exception {
        mockMvc.perform(multipart("/admin/process/processTemplate/uploadProcessDefinition")
                        .file(new MockMultipartFile("file", "noBpmn.zip", "application/zip",
                                zip("readme.txt", "不是流程图")))
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userTasks", empty()));
    }

    @Test
    void saveReplacesRulesForUnpublishedTemplateAndReadBackListsThem() throws Exception {
        long templateId = createUnpublishedTemplate("规则读写模板");

        mockMvc.perform(put("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"taskDefinitionKey":"first","sourceType":"DEPT_LEADER"},
                                 {"taskDefinitionKey":"second","sourceType":"ROLE","targetId":821}]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.userTasks", hasSize(2)))
                .andExpect(jsonPath("$.data.rules", hasSize(2)))
                .andExpect(jsonPath("$.data.rules[0].taskDefinitionKey").value("first"))
                .andExpect(jsonPath("$.data.rules[0].sourceType").value("DEPT_LEADER"))
                .andExpect(jsonPath("$.data.rules[0].targetId").doesNotExist())
                .andExpect(jsonPath("$.data.rules[1].taskDefinitionKey").value("second"))
                .andExpect(jsonPath("$.data.rules[1].sourceType").value("ROLE"))
                .andExpect(jsonPath("$.data.rules[1].targetId").value(821));

        // 整体替换：再保存一次只剩一条，旧行不得残留（每节点单选来源）。
        mockMvc.perform(put("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"taskDefinitionKey":"second","sourceType":"POST","targetId":811}]
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_approver_rule WHERE process_template_id = ?",
                Long.class, templateId));
        assertEquals("POST", jdbcTemplate.queryForObject(
                "SELECT source_type FROM oa_process_approver_rule WHERE process_template_id = ?",
                String.class, templateId));
    }

    @Test
    void saveRejectsUnwritableTemplatesAndBadRuleShapesWithNamedMessages() throws Exception {
        long templateId = createUnpublishedTemplate("规则校验模板");

        // 已发布模板不可改（冻结语义），直接对种子模板 1 保存规则。
        mockMvc.perform(put("/admin/process/processTemplate/approverRules/1")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"DEPT_LEADER\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("已发布的审批模板不能修改审批人规则"));

        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"nope\",\"sourceType\":\"DEPT_LEADER\"}]",
                "审批节点[nope]不存在于流程 zip");
        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"MAGIC\"}]",
                "审批人来源类型无效");
        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"ROLE\",\"targetId\":999999}]",
                "审批节点[一审]的角色不存在或已停用");
        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"ROLE\",\"targetId\":822}]",
                "审批节点[一审]的角色不存在或已停用");
        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"POST\",\"targetId\":812}]",
                "审批节点[一审]的岗位不存在或已停用");
        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"DEPT_LEADER\",\"targetId\":801}]",
                "部门负责人来源按发起人所在部门解析，不指定目标");
        expectRejectedRuleSave(templateId,
                "[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"ROLE\"},{\"taskDefinitionKey\":\"first\",\"sourceType\":\"POST\",\"targetId\":811}]",
                "审批节点[first]重复配置");
    }

    @Test
    void ruleEndpointsRequireTheConfigurationGate() throws Exception {
        long templateId = createUnpublishedTemplate("无权模板");

        mockMvc.perform(put("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", plainToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"DEPT_LEADER\"}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205));
    }

    @Test
    void publishSynthesizesSidecarArtifactAndEvidencePointsAtIt() throws Exception {
        long templateId = createUnpublishedTemplate("合成发布模板");
        saveRules(templateId, """
                [{"taskDefinitionKey":"first","sourceType":"DEPT_LEADER"},
                 {"taskDefinitionKey":"second","sourceType":"ROLE","targetId":821}]
                """);
        String uploadedPath = templatePath(templateId);
        String uploadedSha = sha256Of(uploadedPath);

        publish(templateId);

        // 发布证据指向合成件，不再指向上传原件；原件保留。
        String artifactPath = jdbcTemplate.queryForObject(
                "SELECT artifact_path FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, templateId);
        String artifactSha = jdbcTemplate.queryForObject(
                "SELECT artifact_sha256 FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, templateId);
        assertNotEquals(uploadedPath, artifactPath);
        assertEquals(uploadedSha, sha256Of(uploadedPath), "上传原件必须原样保留");
        assertTrue(artifactPath.startsWith("processes/sha256/"), artifactPath);
        assertEquals(artifactSha, sha256Of(artifactPath), "证据 sha256 必须是合成件的内容摘要");

        // 合成件 = 上传 zip 原条目 + 冻结的 oa-approvers.json。
        List<String> entries = zipEntryNames(artifactPath);
        assertTrue(entries.contains("approver-rule-flow.bpmn20.xml"), String.valueOf(entries));
        assertTrue(entries.contains("oa-approvers.json"), String.valueOf(entries));
        String sidecar = zipEntryContent(artifactPath, "oa-approvers.json");
        assertEquals("first", JsonPath.read(sidecar, "$.rules[0].taskDefinitionKey"));
        assertEquals("DEPT_LEADER", JsonPath.read(sidecar, "$.rules[0].sourceType"));
        assertEquals("second", JsonPath.read(sidecar, "$.rules[1].taskDefinitionKey"));
        assertEquals("ROLE", JsonPath.read(sidecar, "$.rules[1].sourceType"));
        assertEquals(821, (int) JsonPath.<Integer>read(sidecar, "$.rules[1].targetId"));
        assertEquals("approverRuleFlow", JsonPath.read(sidecar, "$.processDefinitionKey"));

        // 发布判定走合成件：证据校验（sha256 复核）与发起解析（resolveForInitiation）都成立。
        var publication = approvalTemplate.resolveForInitiation(templateId);
        assertTrue(publication.processDefinitionId() != null && !publication.processDefinitionId().isBlank());
        assertTrue(publication.template() != null);
    }

    @Test
    void publishWithoutRulesDeploysTheOriginalUpload() throws Exception {
        // 无规则但 BPMN 全部节点带 assignee：发布产物就是上传原件（旧模板形状不变）。
        byte[] assignedZip = zip("assigned-flow.bpmn20.xml", """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:activiti="http://activiti.org/bpmn"
                             targetNamespace="http://bpmn.io/schema/bpmn">
                  <process id="assignedFlow" isExecutable="true">
                    <startEvent id="start"/>
                    <userTask id="only" name="唯一节点" activiti:assignee="zhangsan"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="only"/>
                    <sequenceFlow id="flow2" sourceRef="only" targetRef="end"/>
                  </process>
                </definitions>
                """);
        long templateId = createTemplateWithZip("无规则发布模板", assignedZip, "assignedFlow");
        String uploadedPath = templatePath(templateId);
        String uploadedSha = sha256Of(uploadedPath);

        publish(templateId);

        String artifactPath = jdbcTemplate.queryForObject(
                "SELECT artifact_path FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, templateId);
        String artifactSha = jdbcTemplate.queryForObject(
                "SELECT artifact_sha256 FROM oa_process_template_publication WHERE process_template_id = ?",
                String.class, templateId);
        assertEquals(uploadedPath, artifactPath, "无规则时发布产物就是上传原件（旧模板形状不变）");
        assertEquals(uploadedSha, artifactSha);
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_approver_rule WHERE process_template_id = ?",
                Long.class, templateId));
    }

    @Test
    void publishRejectsNodesWithoutAnySourceAndUnparsableZipsWithNamedMessages() throws Exception {
        long noSource = createUnpublishedTemplate("缺来源模板");
        mockMvc.perform(post("/admin/process/processTemplate/publish/" + noSource)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批节点[一审]缺少审批人来源（配置审批人规则或流程 assignee）"));

        long noBpmn = createTemplateWithZip("无图模板", zip("readme.txt", "不是流程图"));
        mockMvc.perform(post("/admin/process/processTemplate/publish/" + noBpmn)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("流程 zip 里没有可解析的流程图，无法发布"));
    }

    @Test
    void publishRejectsRuleTargetsThatVanishedOrGotDisabledAfterSave() throws Exception {
        // 保存时目标还在，发布前失效：发布校验按同一道目标门拒绝（每节点都有规则，只暴露目标问题）。
        long templateId = createUnpublishedTemplate("目标失效模板");
        saveRules(templateId, """
                [{"taskDefinitionKey":"first","sourceType":"POST","targetId":811},
                 {"taskDefinitionKey":"second","sourceType":"ROLE","targetId":821}]
                """);

        jdbcTemplate.update("UPDATE sys_post SET status = 0 WHERE id = 811");
        mockMvc.perform(post("/admin/process/processTemplate/publish/" + templateId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批节点[一审]的岗位不存在或已停用"));

        jdbcTemplate.update("UPDATE sys_post SET status = 1 WHERE id = 811");
        saveRules(templateId, """
                [{"taskDefinitionKey":"first","sourceType":"POST","targetId":811},
                 {"taskDefinitionKey":"second","sourceType":"ROLE","targetId":821}]
                """);
        jdbcTemplate.update("UPDATE sys_role SET is_deleted = 1 WHERE id = 821");
        mockMvc.perform(post("/admin/process/processTemplate/publish/" + templateId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批节点[二审]的角色不存在或已停用"));
    }

    @Test
    void publishedRulesStayFrozenEvenWhenTheSaveEndpointIsHit() throws Exception {
        long templateId = createUnpublishedTemplate("冻结模板");
        saveRules(templateId, """
                [{"taskDefinitionKey":"first","sourceType":"DEPT_LEADER"},
                 {"taskDefinitionKey":"second","sourceType":"ROLE","targetId":821}]
                """);
        publish(templateId);

        mockMvc.perform(put("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"taskDefinitionKey\":\"first\",\"sourceType\":\"ROLE\",\"targetId\":821}]"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("已发布的审批模板不能修改审批人规则"));
        assertEquals("DEPT_LEADER", jdbcTemplate.queryForObject(
                "SELECT source_type FROM oa_process_approver_rule WHERE process_template_id = ? AND task_definition_key = 'first'",
                String.class, templateId), "已发布模板的规则不得被改写");
    }

    private void expectRejectedRuleSave(long templateId, String body, String message) throws Exception {
        mockMvc.perform(put("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(message));
        assertEquals(0L, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_process_approver_rule WHERE process_template_id = ?",
                Long.class, templateId), "拒绝的保存不得落下半份规则");
    }

    private long createUnpublishedTemplate(String name) throws Exception {
        return createTemplateWithZip(name, flowZip());
    }

    private long createTemplateWithZip(String name, byte[] zipBytes) throws Exception {
        return createTemplateWithZip(name, zipBytes, "approverRuleFlow");
    }

    private long createTemplateWithZip(String name, byte[] zipBytes, String processDefinitionKey) throws Exception {
        String upload = mockMvc.perform(multipart("/admin/process/processTemplate/uploadProcessDefinition")
                        .file(new MockMultipartFile("file", processDefinitionKey + ".zip", "application/zip", zipBytes))
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn().getResponse().getContentAsString();
        String path = JsonPath.read(upload, "$.data.processDefinitionPath");
        mockMvc.perform(post("/admin/process/processTemplate/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + name + "\",\"processTypeId\":1,"
                                + "\"processDefinitionKey\":\"" + processDefinitionKey + "\","
                                + "\"processDefinitionPath\":\"" + path + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM oa_process_template WHERE name = ?", Long.class, name);
    }

    private void saveRules(long templateId, String body) throws Exception {
        mockMvc.perform(put("/admin/process/processTemplate/approverRules/" + templateId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private void publish(long templateId) throws Exception {
        mockMvc.perform(post("/admin/process/processTemplate/publish/" + templateId)
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    private String templatePath(long templateId) {
        return jdbcTemplate.queryForObject(
                "SELECT process_definition_path FROM oa_process_template WHERE id = ?",
                String.class, templateId);
    }

    private String sha256Of(String relativePath) throws IOException {
        Path directory = Path.of("target/test-process-definitions").toAbsolutePath();
        return com.lautung.oa.process.ProcessDefinitionStorage.sha256(directory.resolve(relativePath));
    }

    private List<String> zipEntryNames(String relativePath) throws IOException {
        Path directory = Path.of("target/test-process-definitions").toAbsolutePath();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(directory.resolve(relativePath)))) {
            ZipEntry entry;
            List<String> names = new java.util.ArrayList<>();
            while ((entry = zip.getNextEntry()) != null) {
                names.add(entry.getName());
            }
            return names;
        }
    }

    private String zipEntryContent(String relativePath, String entryName) throws IOException {
        Path directory = Path.of("target/test-process-definitions").toAbsolutePath();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(directory.resolve(relativePath)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entryName.equals(entry.getName())) {
                    return new String(zip.readAllBytes(), StandardCharsets.UTF_8);
                }
            }
        }
        throw new IllegalStateException("合成件缺少条目 " + entryName);
    }

    private static byte[] zip(String entryName, String content) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry(entryName));
            zip.write(content.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    /** 类路径 BPMN 原样打成流程 zip（与 ApprovalContractTest 的 approvalTwoStepZip 同一做法）。 */
    private static byte[] flowZip() throws IOException {
        byte[] bpmn;
        try (InputStream bpmnStream = ApproverRuleContractTest.class
                .getClassLoader().getResourceAsStream("process/approver-rule-flow.bpmn20.xml")) {
            if (bpmnStream == null) {
                throw new IllegalStateException("缺少测试流程定义");
            }
            bpmn = bpmnStream.readAllBytes();
        }
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("approver-rule-flow.bpmn20.xml"));
            zip.write(bpmn);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private String adminToken() throws Exception {
        return loginToken("admin");
    }

    private String plainToken() throws Exception {
        return loginToken("eve");
    }

    private String loginToken(String username) {
        Long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = ?", Long.class, username);
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }
}
