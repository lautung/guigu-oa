package com.lautung.oa.auth;

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
    public void approveWithoutStatusReturnsTheValidationMessage() throws Exception {
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":1,\"taskId\":\"task-1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批状态不能为空"));
    }

    @Test
    public void approveWithAnOutOfRangeStatusIsRejected() throws Exception {
        // status 非法在成员资格判定之前拒绝（校验先于授权，ADR 0034）：不带真实待办也应得到 201。
        mockMvc.perform(post("/admin/process/approve")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"processId\":1,\"taskId\":\"task-1\",\"status\":0}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批状态只能为 1（通过）或 -1（拒绝）"));
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
    public void menuSaveWithAnOutOfRangeTypeIsRejected() throws Exception {
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":1,\"name\":\"异常菜单\",\"type\":5,\"path\":\"odd\",\"component\":\"system/sysUser/list\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单类型只能为 0 或 1"));
    }

    @Test
    public void menuSavePageWithoutComponentIsRejected() throws Exception {
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"parentId\":1,\"name\":\"无组件页\",\"type\":1,\"path\":\"barePage\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("类型为菜单时组件路径不能为空"));
    }

    @Test
    public void menuSaveWithOnlyTypeJoinsTheComponentMessageInFieldOrder() throws Exception {
        mockMvc.perform(post("/admin/system/sysMenu/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"type\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(
                        "菜单名称不能为空；类型为菜单时组件路径不能为空；上级菜单不能为空；路由地址不能为空"));
    }

    @Test
    public void menuUpdateWithABlankedComponentIsRejected() throws Exception {
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"type\":1,\"component\":\"   \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("类型为菜单时组件路径不能为空"));
    }

    @Test
    public void menuUpdateWithOverLengthNameIsRejected() throws Exception {
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"" + "菜".repeat(51) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单名称过长"));
    }

    @Test
    public void menuUpdateWithAnOutOfRangeTypeIsRejected() throws Exception {
        mockMvc.perform(put("/admin/system/sysMenu/update")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"type\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("菜单类型只能为 0 或 1"));
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
    public void sysUserSaveWithOverLengthNameAndUsernameIsRejected() throws Exception {
        mockMvc.perform(post("/admin/system/sysUser/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + "a".repeat(21) + "\",\"password\":\"111111\","
                                + "\"name\":\"" + "名".repeat(21) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("姓名过长；用户名过长"));
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
    public void sysRoleSaveWithOverLengthNameAndCodeIsRejected() throws Exception {
        mockMvc.perform(post("/admin/system/sysRole/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roleName\":\"" + "角".repeat(21) + "\","
                                + "\"roleCode\":\"" + "c".repeat(21) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("角色编码过长；角色名称过长"));
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
    public void processTypeSaveWithTooLongNameIsRejected() throws Exception {
        mockMvc.perform(post("/admin/process/processType/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "型".repeat(51) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批类型名称过长"));
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

    @Test
    public void processTemplateSaveWithTooLongNameIsRejected() throws Exception {
        mockMvc.perform(post("/admin/process/processTemplate/save")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "模".repeat(51) + "\",\"processTypeId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("审批模板名称过长"));
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
