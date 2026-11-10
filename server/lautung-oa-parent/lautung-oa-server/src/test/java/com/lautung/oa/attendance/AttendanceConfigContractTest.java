package com.lautung.oa.attendance;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * #278 的考勤配置合同：班次/考勤组（含成员）/节假日 CRUD、
 * 班次时刻校验 201 命名拒绝、成员一人一组与硬删重建、节假日日期唯一与硬删重建、
 * 成员候选只列启用用户、页面/按钮 205 矩阵。
 *
 * <p>夹具统一 t278 前缀；拒绝用例都用良构体，避免校验先于 gate 的顺序把断言带偏（ADR 0034）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_attendance_config;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class AttendanceConfigContractTest {

    private static final long PAGE_ONLY_ROLE_ID = 2780L;
    private static final long PAGE_ONLY_USER_ID = 2780L;
    private static final String GROUP_PAGE = "/business/attendance/group";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @BeforeEach
    @AfterEach
    void resetFixtures() {
        jdbcTemplate.update("DELETE FROM attendance_group_member WHERE group_id IN "
                + "(SELECT id FROM attendance_group WHERE name LIKE 't278%')");
        jdbcTemplate.update("DELETE FROM attendance_group WHERE name LIKE 't278%'");
        jdbcTemplate.update("DELETE FROM attendance_shift WHERE name LIKE 't278%'");
        jdbcTemplate.update("DELETE FROM attendance_holiday WHERE name LIKE 't278%'");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id = ?", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id = ? OR role_id = ?",
                PAGE_ONLY_USER_ID, PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_role WHERE id = ?", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", PAGE_ONLY_USER_ID);
    }

    @BeforeEach
    void seedUsers() {
        jdbcTemplate.update("MERGE INTO sys_user (id, username, password, name, status, is_deleted) "
                + "KEY (id) VALUES (?, ?, '', ?, 1, 0)", 27801L, "t278-alice", "甲");
        jdbcTemplate.update("MERGE INTO sys_user (id, username, password, name, status, is_deleted) "
                + "KEY (id) VALUES (?, ?, '', ?, 1, 0)", 27802L, "t278-bob", "乙");
        jdbcTemplate.update("MERGE INTO sys_user (id, username, password, name, status, is_deleted) "
                + "KEY (id) VALUES (?, ?, '', ?, 0, 0)", 27803L, "t278-carol", "停用丙");
    }

    @AfterEach
    void cleanUsers() {
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (27801, 27802, 27803)");
    }

    @Test
    void shiftCrudValidatesTimesAndGuardsBoundRemoval() throws Exception {
        long shiftId = saveShift("t278班", "09:00:00", "18:00:00", "12:00:00", "13:00:00", 10, "标准班");

        mockMvc.perform(get("/admin/attendance/shift/1/10?name=t278").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) shiftId))
                .andExpect(jsonPath("$.data.records[0].name").value("t278班"))
                .andExpect(jsonPath("$.data.records[0].startTime").value("09:00:00"))
                .andExpect(jsonPath("$.data.records[0].endTime").value("18:00:00"))
                .andExpect(jsonPath("$.data.records[0].graceMinutes").value(10));

        mockMvc.perform(get("/admin/attendance/shift/options").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].name").value("t278班"));

        // 更新按完整表单覆盖：午休成对传 null 即清除，宽限与名称同批覆盖。
        mockMvc.perform(put("/admin/attendance/shift/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + shiftId + ",\"name\":\"t278班改\","
                                + "\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\","
                                + "\"lunchStart\":null,\"lunchEnd\":null,\"graceMinutes\":15,"
                                + "\"remark\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(15, jdbcTemplate.queryForObject(
                "SELECT grace_minutes FROM attendance_shift WHERE id = ?", Integer.class, shiftId));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_shift WHERE id = " + shiftId
                + " AND lunch_start IS NOT NULL"));

        // 时刻校验 201 命名拒绝。
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"倒挂\",\"startTime\":\"18:00:00\",\"endTime\":\"09:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("下班时刻必须晚于上班时刻"));
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"相等\",\"startTime\":\"09:00:00\",\"endTime\":\"09:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("下班时刻必须晚于上班时刻"));
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"半截午休\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\","
                                + "\"lunchStart\":\"12:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("午休开始与结束必须同时填写"));
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无序午休\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\","
                                + "\"lunchStart\":\"13:00:00\",\"lunchEnd\":\"12:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("午休结束必须晚于午休开始"));
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"负宽限\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\","
                                + "\"graceMinutes\":-1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(containsString("宽限分钟不能为负数")));
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value(containsString("班次名称不能为空")));

        // 更新时按载荷整体校验：下班改到早于上班也被拒。
        mockMvc.perform(put("/admin/attendance/shift/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + shiftId + ",\"name\":\"t278班改\","
                                + "\"startTime\":\"09:00:00\",\"endTime\":\"08:00:00\","
                                + "\"graceMinutes\":15,\"remark\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("下班时刻必须晚于上班时刻"));

        // 未被绑定的班次可删；删除后再建同名班次不受限。
        mockMvc.perform(delete("/admin/attendance/shift/remove/" + shiftId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_shift WHERE id = " + shiftId
                + " AND is_deleted = 0"));
    }

    @Test
    void groupCrudReplacesMembersEnforcesOneGroupPerUserAndHardDeletes() throws Exception {
        long shiftId = saveShift("t278班", "09:00:00", "18:00:00", null, null, 0, "");

        mockMvc.perform(post("/admin/attendance/group/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t278组\",\"shiftId\":" + shiftId + ","
                                + "\"workdays\":[1,2,3,4,5],\"memberUserIds\":[27801,27802],"
                                + "\"remark\":\"研发组\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        long groupId = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_group WHERE name = 't278组' AND is_deleted = 0", Long.class);

        mockMvc.perform(get("/admin/attendance/group/1/10?name=t278").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value((int) groupId))
                .andExpect(jsonPath("$.data.records[0].name").value("t278组"))
                .andExpect(jsonPath("$.data.records[0].shiftId").value((int) shiftId))
                .andExpect(jsonPath("$.data.records[0].shiftName").value("t278班"))
                .andExpect(jsonPath("$.data.records[0].workdays").value("1,2,3,4,5"))
                .andExpect(jsonPath("$.data.records[0].memberCount").value(2));

        mockMvc.perform(get("/admin/attendance/group/" + groupId + "/members")
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].username").value("t278-alice"))
                .andExpect(jsonPath("$.data[0].name").value("甲"))
                .andExpect(jsonPath("$.data[1].username").value("t278-bob"));

        // 成员候选只列启用用户，不依赖 auth 与用户管理页权限。
        mockMvc.perform(get("/admin/attendance/group/userOptions").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[*].username", hasItems("t278-alice", "t278-bob")))
                .andExpect(jsonPath("$.data[*].username", not(hasItem("t278-carol"))));

        // 第二个组收同一个成员：一人最多一组 201 命名拒绝。
        mockMvc.perform(post("/admin/attendance/group/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t278组二\",\"shiftId\":" + shiftId + ","
                                + "\"workdays\":[1,2,3,4,5],\"memberUserIds\":[27801]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该用户已加入其他考勤组"));

        // 停用用户不能入组。
        mockMvc.perform(post("/admin/attendance/group/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t278组三\",\"shiftId\":" + shiftId + ","
                                + "\"workdays\":[1,2,3,4,5],\"memberUserIds\":[27803]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("考勤成员不存在或已停用"));

        // update：成员缺省保留、空列表清空、显式列表整体替换；workdays 缺省不覆盖。
        mockMvc.perform(put("/admin/attendance/group/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + groupId + ",\"name\":\"t278组改名\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(2, count("SELECT COUNT(*) FROM attendance_group_member WHERE group_id = " + groupId));

        mockMvc.perform(put("/admin/attendance/group/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + groupId + ",\"workdays\":[6],\"memberUserIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("6", jdbcTemplate.queryForObject(
                "SELECT workdays FROM attendance_group WHERE id = ?", String.class, groupId));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_group_member WHERE group_id = " + groupId));

        mockMvc.perform(put("/admin/attendance/group/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + groupId + ",\"memberUserIds\":[27802]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_group_member WHERE group_id = " + groupId));

        // 非法工作日拒绝。
        mockMvc.perform(put("/admin/attendance/group/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + groupId + ",\"workdays\":[9]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("每周工作日不合法"));

        // 绑定不存在的班次拒绝。
        mockMvc.perform(put("/admin/attendance/group/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + groupId + ",\"shiftId\":999999}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("绑定的班次不存在"));

        // 组删除连带清成员行（硬删）；随后同一成员可重建（uk_attendance_member_user 不再占用）。
        mockMvc.perform(delete("/admin/attendance/group/remove/" + groupId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_group WHERE id = " + groupId
                + " AND is_deleted = 0"));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_group_member WHERE group_id = " + groupId));

        mockMvc.perform(post("/admin/attendance/group/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t278组重建\",\"shiftId\":" + shiftId + ","
                                + "\"workdays\":[1,2,3,4,5],\"memberUserIds\":[27802]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void shiftBoundByGroupCannotBeRemoved() throws Exception {
        long shiftId = saveShift("t278绑定班", "09:00:00", "18:00:00", null, null, 0, "");
        mockMvc.perform(post("/admin/attendance/group/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t278绑定组\",\"shiftId\":" + shiftId + ","
                                + "\"workdays\":[1,2,3,4,5]}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(delete("/admin/attendance/shift/remove/" + shiftId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("仍有考勤组绑定该班次，不能删除"));
        assertEquals(1, count("SELECT COUNT(*) FROM attendance_shift WHERE id = " + shiftId
                + " AND is_deleted = 0"));
    }

    @Test
    void holidayCrudIsUniquePerDateAndHardDeletesAllowRebuild() throws Exception {
        mockMvc.perform(post("/admin/attendance/holiday/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-10-01\",\"name\":\"国庆\",\"dayType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        long holidayId = jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_holiday WHERE holiday_date = '2026-10-01'", Long.class);

        mockMvc.perform(post("/admin/attendance/holiday/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-10-01\",\"name\":\"重复日期\",\"dayType\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("该日期已存在节假日"));

        mockMvc.perform(post("/admin/attendance/holiday/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-10-02\",\"name\":\"非法类型\",\"dayType\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("节假日类型不合法"));

        mockMvc.perform(get("/admin/attendance/holiday/1/10?dayType=1").header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("国庆"));

        mockMvc.perform(put("/admin/attendance/holiday/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + holidayId + ",\"name\":\"国庆节\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals("国庆节", jdbcTemplate.queryForObject(
                "SELECT name FROM attendance_holiday WHERE id = ?", String.class, holidayId));

        mockMvc.perform(put("/admin/attendance/holiday/update").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + holidayId + ",\"dayType\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("节假日类型不合法"));

        // 硬删后同日期可重建。
        mockMvc.perform(delete("/admin/attendance/holiday/remove/" + holidayId).header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, count("SELECT COUNT(*) FROM attendance_holiday WHERE holiday_date = '2026-10-01'"));
        mockMvc.perform(post("/admin/attendance/holiday/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-10-01\",\"name\":\"国庆重建\",\"dayType\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void attendanceRoutesGateByPageAndButtons() throws Exception {
        long shiftId = saveShift("t278gate班", "09:00:00", "18:00:00", null, null, 0, "");
        String plainToken = userToken();

        // 读端点 205：页面未授予。
        mockMvc.perform(get("/admin/attendance/shift/1/10").header("token", plainToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(get("/admin/attendance/shift/options").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/attendance/group/1/10").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/attendance/group/userOptions").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get("/admin/attendance/holiday/1/10").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));

        // 写端点 205：良构体才轮到 gate（校验先于 gate，ADR 0034）。
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无权\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(put("/admin/attendance/shift/update").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":" + shiftId + ",\"name\":\"改名\",\"startTime\":\"09:00:00\","
                                + "\"endTime\":\"18:00:00\",\"graceMinutes\":0,\"remark\":\"\"}"))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/attendance/shift/remove/" + shiftId).header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/attendance/group/save").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"无权组\",\"shiftId\":" + shiftId + ",\"workdays\":[1]}"))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(put("/admin/attendance/group/update").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"id\":1,\"name\":\"改名\"}"))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/attendance/group/remove/1").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/attendance/holiday/save").header("token", plainToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"holidayDate\":\"2026-05-01\",\"name\":\"无权假\",\"dayType\":1}"))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(delete("/admin/attendance/holiday/remove/1").header("token", plainToken))
                .andExpect(jsonPath("$.code").value(205));

        // 只授页面：读全通，写 205。
        insertPageOnlyPrincipal();
        String pageToken = jwtHelper.createTokenPair(PAGE_ONLY_USER_ID, "t278-page", 0L).token();
        mockMvc.perform(get("/admin/attendance/shift/options").header("token", pageToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/attendance/group/userOptions").header("token", pageToken))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"页面用户\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}"))
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        // 补按钮点后写放行。
        grantButton("attendance:group:add");
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", pageToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"t278按钮班\",\"startTime\":\"09:00:00\",\"endTime\":\"18:00:00\"}"))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/index/info").header("token", adminToken()))
                .andExpect(jsonPath("$.data.permissions", hasItems(
                        "attendance:group:add", "attendance:group:edit", "attendance:group:remove")))
                .andExpect(jsonPath("$.data.menus[*].path", hasItems(GROUP_PAGE)));
    }

    private long saveShift(String name, String start, String end,
                           String lunchStart, String lunchEnd, int grace, String remark)
            throws Exception {
        StringBuilder body = new StringBuilder()
                .append("{\"name\":\"").append(name).append("\",")
                .append("\"startTime\":\"").append(start).append("\",")
                .append("\"endTime\":\"").append(end).append("\",")
                .append("\"graceMinutes\":").append(grace).append(",")
                .append("\"remark\":\"").append(remark).append("\"");
        if (lunchStart != null) {
            body.append(",\"lunchStart\":\"").append(lunchStart).append("\"")
                    .append(",\"lunchEnd\":\"").append(lunchEnd).append("\"");
        }
        body.append("}");
        mockMvc.perform(post("/admin/attendance/shift/save").header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM attendance_shift WHERE name = ? AND is_deleted = 0",
                Long.class, name);
    }

    private void insertPageOnlyPrincipal() {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, data_scope, is_deleted) "
                + "VALUES (?, 'T278角色', 't278_role', 1, 0)", PAGE_ONLY_ROLE_ID);
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, 't278-page', '', 'T278用户', 1, 0)", PAGE_ONLY_USER_ID);
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ONLY_USER_ID, PAGE_ONLY_ROLE_ID);
        grantMenu(menuIdByPath(GROUP_PAGE));
    }

    private void grantButton(String perms) {
        grantMenu(menuIdByPerms(perms));
    }

    private void grantMenu(long menuId) {
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                PAGE_ONLY_ROLE_ID, menuId);
    }

    private long menuIdByPath(String path) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = ? AND is_deleted = 0", Long.class, path);
    }

    private long menuIdByPerms(String perms) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND is_deleted = 0", Long.class, perms);
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin", 0L).token();
    }

    private String userToken() {
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_user WHERE username = 'user'", Long.class);
        return jwtHelper.createTokenPair(userId, "user", 0L).token();
    }

    private int count(String sql) {
        Integer result = jdbcTemplate.queryForObject(sql, Integer.class);
        return result == null ? 0 : result;
    }
}
