package com.atguigu.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 参数校验的 HTTP 契约（ADR 0033）：必填为空是明确的业务失败（HTTP 200 + code 201 + 中文消息），
 * 不是 403、也不是泛化的「执行全局异常处理」。登录在过滤器里校验，用例在
 * {@link LoginAuthContractTest}；容器级 404/405 的语义见 {@link HttpErrorSemanticsContractTest}。
 *
 * <p>配置类写请求体的 DTO 顺序契约见 ADR 0034：认证（过滤器）先于校验，校验先于配置 gate。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_request_validation;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Sql("/auth-contract-schema.sql")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
public class RequestValidationContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void startUpWithoutTemplateIdReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批模板不能为空"));
    }

    @Test
    public void startUpWithMalformedFormValuesIsRejected() throws Exception {
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processTemplateId\":1,\"formValues\":\"{not-json\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("表单内容格式无效"));
    }

    @Test
    public void approveWithoutProcessIdReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"taskId\":\"task-1\",\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批标识不能为空"));
    }

    @Test
    public void approveWithoutTaskIdReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":1,\"status\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("待办任务标识不能为空"));
    }

    @Test
    public void attachmentUploadWithoutFileNameReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sizeBytes\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("附件文件名不能为空"));
    }

    @Test
    public void attachmentUploadWithoutSizeReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.txt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("附件大小不能为空"));
    }

    @Test
    public void attachmentUploadWithBlankFileNameReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"  \",\"sizeBytes\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("附件文件名不能为空"));
    }

    @Test
    public void attachmentUploadWithTooLongFileNameReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"" + "a".repeat(513) + "\",\"sizeBytes\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("附件文件名过长"));
    }

    @Test
    public void attachmentUploadWithNonPositiveSizeReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"a.txt\",\"sizeBytes\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("附件大小必须大于 0"));
    }

    @Test
    public void multipleMissingFieldsAreJoinedInFieldOrder() throws Exception {
        mockMvc.perform(post("/admin/attachments/uploads")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("附件文件名不能为空；附件大小不能为空"));
    }

    @Test
    public void aMissingBodyIsReportedAsAnUnreadableRequest() throws Exception {
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请求体格式无效"));
    }

    @Test
    public void malformedJsonIsReportedAsAnUnreadableRequest() throws Exception {
        mockMvc.perform(post("/admin/process/startUp")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("请求体格式无效"));
    }

    @Test
    public void authenticationIsRejectedBeforeValidationRuns() throws Exception {
        mockMvc.perform(post("/admin/process/startUp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void authenticationIsRejectedBeforeConfigurationValidationRuns() throws Exception {
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    public void configurationValidationRunsBeforeTheGrantGate() throws Exception {
        // 这个库里 admin 没有任何角色授予行：能拿到 201 而不是 205，就说明校验在配置 gate 之前（ADR 0034）。
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单名称不能为空；上级菜单不能为空；路由地址不能为空；菜单类型不能为空"));
    }

    @Test
    public void sysUserSaveWithoutCredentialsReturnsTheValidationMessages() throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("姓名不能为空；密码不能为空；用户名不能为空"));
    }

    @Test
    public void sysUserSaveWithABlankPasswordIsRejected() throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"new-user\",\"password\":\"  \",\"name\":\"新用户\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码不能为空"));
    }

    @Test
    public void sysUserUpdateWithoutIdReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(put("/admin/system/sysUser/update")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"renamed\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("用户标识不能为空"));
    }

    @Test
    public void sysRoleSaveWithoutNameOrCodeReturnsTheValidationMessages() throws Exception {
        mockMvc.perform(post("/admin/system/sysRole/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("角色编码不能为空；角色名称不能为空"));
    }

    @Test
    public void roleAssignWithoutUserReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/system/sysRole/doAssign")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleIdList\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("用户标识不能为空"));
    }

    @Test
    public void menuAssignWithoutRoleReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/system/sysMenu/doAssign")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"menuIdList\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("角色标识不能为空"));
    }

    @Test
    public void processTypeSaveWithoutNameReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/process/processType/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批类型名称不能为空"));
    }

    @Test
    public void processTemplateSaveWithoutNameOrTypeReturnsTheValidationMessages() throws Exception {
        mockMvc.perform(post("/admin/process/processTemplate/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批模板名称不能为空；审批类型不能为空"));
    }

    @Test
    public void processTemplateUpdateWithoutIdReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(put("/admin/process/processTemplate/update")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"请假\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批模板标识不能为空"));
    }

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"111111\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        return JsonPath.read(response, "$.data.token");
    }
}
