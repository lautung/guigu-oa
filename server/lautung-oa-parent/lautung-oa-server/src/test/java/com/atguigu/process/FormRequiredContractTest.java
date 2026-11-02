package com.atguigu.process;

import com.atguigu.common.config.exception.GuiguException;
import com.atguigu.model.process.ProcessTemplate;
import com.atguigu.model.system.SysUser;
import com.atguigu.process.service.Approval;
import com.atguigu.process.service.ApprovalTemplate;
import com.atguigu.security.custom.LoginUserInfoHelper;
import com.atguigu.vo.process.ProcessFormVo;
import org.flowable.engine.HistoryService;
import org.flowable.engine.RepositoryService;
import org.flowable.engine.RuntimeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * 已发布模板动态必填的发起契约（ADR 0035）：必填为空是一次明确的 201 失败，
 * 不产生审批；没有必填字段的模板与解析不了的 formProps 不受影响（fail-open）。
 * 空值定义与规则形状的判定细节见 {@link ProcessStartSupportTest}。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_form_required;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@ActiveProfiles("test")
@Sql({"/auth-contract-schema.sql", "/approval-contract-schema.sql"})
@Import(ApprovalContractTest.InMemoryRedisConfig.class)
public class FormRequiredContractTest {

    private static final String TWO_REQUIRED =
            "[{\"type\":\"input\",\"field\":\"reason\",\"title\":\"请假原因\",\"$required\":true},"
                    + "{\"type\":\"input\",\"field\":\"days\",\"title\":\"请假天数\",\"$required\":true}]";
    private static final String NONE_REQUIRED =
            "[{\"type\":\"input\",\"field\":\"reason\",\"title\":\"请假原因\"},"
                    + "{\"type\":\"input\",\"field\":\"days\",\"title\":\"请假天数\"}]";

    @Autowired
    private Approval approval;

    @Autowired
    private ApprovalTemplate approvalTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepositoryService repositoryService;

    @Autowired
    private RuntimeService runtimeService;

    @Autowired
    private HistoryService historyService;

    @BeforeEach
    public void resetEngineState() {
        LoginUserInfoHelper.removeUserId();
        LoginUserInfoHelper.removeUsername();
        clearEngineInstances();
        repositoryService.createProcessDefinitionQuery()
                .processDefinitionKey("approvalTwoStep")
                .list()
                .forEach(definition -> repositoryService.deleteDeployment(definition.getDeploymentId(), true));
    }

    @Test
    public void emptyRequiredFieldIsRefusedBeforeAnythingIsWritten() throws IOException {
        publishLeaveTemplate(TWO_REQUIRED);

        GuiguException failure = assertThrows(GuiguException.class, () ->
                approval.startUp(alice(), leaveForm("{\"formData\":{\"days\":1}}")));
        assertEquals(201, failure.getCode());
        assertEquals("请假原因不能为空", failure.getMsg());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process_record", Integer.class));
    }

    @Test
    public void missingFormValuesReportsEveryRequiredFieldInRuleOrder() throws IOException {
        publishLeaveTemplate(TWO_REQUIRED);

        GuiguException failure = assertThrows(GuiguException.class, () ->
                approval.startUp(alice(), leaveForm(null)));
        assertEquals(201, failure.getCode());
        assertEquals("请假原因不能为空；请假天数不能为空", failure.getMsg());
    }

    @Test
    public void stringRequiredProducesTheCustomMessage() throws IOException {
        publishLeaveTemplate("[{\"type\":\"input\",\"field\":\"reason\",\"title\":\"请假原因\","
                + "\"$required\":\"必须说明请假事由\"}]");

        GuiguException failure = assertThrows(GuiguException.class, () ->
                approval.startUp(alice(), leaveForm("{\"formData\":{}}")));
        assertEquals(201, failure.getCode());
        assertEquals("必须说明请假事由", failure.getMsg());
    }

    @Test
    public void filledRequiredFieldsStartTheApproval() throws IOException {
        publishLeaveTemplate(TWO_REQUIRED);

        approval.startUp(alice(), leaveForm("{\"formData\":{\"reason\":\"家庭事务\",\"days\":1}}"));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
    }

    @Test
    public void whitespaceAndZeroCountAsFilled() throws IOException {
        publishLeaveTemplate(TWO_REQUIRED);

        approval.startUp(alice(), leaveForm("{\"formData\":{\"reason\":\"  \",\"days\":0}}"));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
    }

    @Test
    public void templateWithoutRequiredFieldsAcceptsEmptyFormValues() throws IOException {
        publishLeaveTemplate(NONE_REQUIRED);

        approval.startUp(alice(), leaveForm(null));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
    }

    @Test
    public void unparsableFormPropsDoesNotBlockStart() throws IOException {
        publishLeaveTemplate("{not-json");

        approval.startUp(alice(), leaveForm(null));

        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_process", Integer.class));
    }

    private void publishLeaveTemplate(String formProps) throws IOException {
        jdbcTemplate.update("UPDATE oa_process_template SET status = 0 WHERE id = 1");
        ProcessTemplate template = approvalTemplate.read(1L);
        template.setFormProps(formProps);
        var uploaded = approvalTemplate.uploadProcessDefinition(new MockMultipartFile(
                "file", "approvalTwoStep.zip", "application/zip", approvalTwoStepZip()));
        template.setProcessDefinitionKey(uploaded.processDefinitionKey());
        template.setProcessDefinitionPath(uploaded.processDefinitionPath());
        approvalTemplate.saveUnpublished(template);
        approvalTemplate.publish(template.getId());
    }

    private void clearEngineInstances() {
        runtimeService.createProcessInstanceQuery().list()
                .forEach(instance -> runtimeService.deleteProcessInstance(instance.getId(), "contract-reset"));
        historyService.createHistoricProcessInstanceQuery().list()
                .forEach(instance -> historyService.deleteHistoricProcessInstance(instance.getId()));
    }

    private static byte[] approvalTwoStepZip() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (InputStream bpmn = FormRequiredContractTest.class.getClassLoader()
                     .getResourceAsStream("process/approval-two-step.bpmn20.xml");
             ZipOutputStream zip = new ZipOutputStream(bytes)) {
            if (bpmn == null) throw new IOException("审批合同 BPMN 不存在");
            zip.putNextEntry(new ZipEntry("approval-two-step.bpmn20.xml"));
            bpmn.transferTo(zip);
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static ProcessFormVo leaveForm(String formValues) {
        ProcessFormVo form = new ProcessFormVo();
        form.setProcessTemplateId(1L);
        form.setProcessTypeId(1L);
        form.setFormValues(formValues);
        return form;
    }

    private static SysUser alice() {
        SysUser alice = new SysUser();
        alice.setId(2L);
        alice.setUsername("alice");
        alice.setName("小艾");
        return alice;
    }
}
