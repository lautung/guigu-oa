package com.lautung.oa.notice;

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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 通知公告管理侧 HTTP 合同（ADR 0055/0056，#218）：真实 Flyway（含 V116 的页面与按钮点）
 * 与 H2。钉住页面授予覆盖读、写各要按钮点、校验先于 gate、三态状态机、发布人快照、
 * 列表不带正文、软删即隐式撤回、管理详情带草稿正文。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_notice_admin_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class NoticeAdminContractTest {

    private static final long NO_GRANT_USER = 301L;
    private static final long PAGE_ONLY_USER = 302L;
    private static final long ADD_ONLY_USER = 303L;
    private static final long FULL_USER = 304L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @BeforeEach
    void seedUsers() {
        cleanFixtures();
        insertUser(NO_GRANT_USER, "notice-none");
        insertUser(PAGE_ONLY_USER, "notice-page");
        insertUser(ADD_ONLY_USER, "notice-add");
        insertUser(FULL_USER, "notice-full");

        insertRole(701L, "notice_page");
        insertRole(702L, "notice_add");
        insertRole(703L, "notice_all");
        grantPage(701L);
        grantPage(702L);
        grantPage(703L);
        grantButton(702L, "system:notice:add");
        for (String permission : new String[]{
                "system:notice:add", "system:notice:edit", "system:notice:remove", "system:notice:publish"}) {
            grantButton(703L, permission);
        }
        insertUserRole(PAGE_ONLY_USER, 701L);
        insertUserRole(ADD_ONLY_USER, 702L);
        insertUserRole(FULL_USER, 703L);
    }

    @AfterEach
    void removeFixtures() {
        cleanFixtures();
    }

    @Test
    void createStoresDraftWithSanitizedContentAndListHidesContent() throws Exception {
        mockMvc.perform(createNotice(adminToken(), "  公告标题  ", 1,
                        "<script>alert(1)</script><p onclick=\"x\">正文</p>"
                                + "<img src=\"https://evil.test/a.png\">"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("公告标题"))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.content").value("<p>正文</p>"))
                .andExpect(jsonPath("$.data.publisherId").doesNotExist())
                .andExpect(jsonPath("$.data.publisherName").doesNotExist())
                .andExpect(jsonPath("$.data.publishTime").doesNotExist());

        String stored = jdbcTemplate.queryForObject(
                "SELECT content FROM oa_notice WHERE title = '公告标题'", String.class);
        assertEquals("<p>正文</p>", stored, "库里存的必须是消毒后的 HTML");
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_notice WHERE content LIKE '%script%' OR content LIKE '%onclick%'",
                Integer.class));

        mockMvc.perform(get("/admin/system/notices")
                        .header("token", adminToken())
                        .param("page", "1")
                        .param("limit", "10"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].title").value("公告标题"))
                .andExpect(jsonPath("$.data.records[0].status").value(0))
                // 管理列表也不返回正文。
                .andExpect(jsonPath("$.data.records[0].content").doesNotExist());

        Integer noticeId = jdbcTemplate.queryForObject(
                "SELECT id FROM oa_notice WHERE title = '公告标题'", Integer.class);
        mockMvc.perform(get("/admin/system/notices/" + noticeId).header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.content").value("<p>正文</p>"))
                .andExpect(jsonPath("$.data.status").value(0));
    }

    @Test
    void validationRunsBeforeGate() throws Exception {
        // 无页面授予 + 畸形体：校验先于 gate（201，不是 205）。
        mockMvc.perform(post("/admin/system/notices")
                        .header("token", token(NO_GRANT_USER, "notice-none"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告标题不能为空；公告类型不能为空"));

        mockMvc.perform(createNotice(token(NO_GRANT_USER, "notice-none"), "x".repeat(101), 1, "body"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告标题过长"));

        mockMvc.perform(createNotice(token(NO_GRANT_USER, "notice-none"), "标题", 3, "body"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告类型无效"));

        mockMvc.perform(createNotice(token(NO_GRANT_USER, "notice-none"), "标题", 1, "x".repeat(100001)))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告正文过长"));

        // 良构体才轮到 gate：无页面授予 → 205。
        mockMvc.perform(createNotice(token(NO_GRANT_USER, "notice-none"), "标题", 1, "<p>ok</p>"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));

        // PUT 同样先校验：畸形体对无授予用户也是 201。
        Integer noticeId = createDraftDirectly(6401L, "已有公告", 0);
        mockMvc.perform(put("/admin/system/notices/" + noticeId)
                        .header("token", token(NO_GRANT_USER, "notice-none"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告标题不能为空；公告类型不能为空"));
    }

    @Test
    void pageGrantOpensReadsWhileWritesNeedButtons() throws Exception {
        Integer noticeId = createDraftDirectly(6402L, "权限样本", 0);

        // 只有页面授予：读 200，所有写 205。
        mockMvc.perform(get("/admin/system/notices").header("token", token(PAGE_ONLY_USER, "notice-page")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/notices/" + noticeId)
                        .header("token", token(PAGE_ONLY_USER, "notice-page")))
                .andExpect(jsonPath("$.code").value(200));

        for (RequestBuilder write : new RequestBuilder[]{
                createNotice(token(PAGE_ONLY_USER, "notice-page"), "按钮", 1, "<p>x</p>"),
                updateNotice(token(PAGE_ONLY_USER, "notice-page"), noticeId),
                delete("/admin/system/notices/" + noticeId)
                        .header("token", token(PAGE_ONLY_USER, "notice-page")),
                post("/admin/system/notices/" + noticeId + "/publish")
                        .header("token", token(PAGE_ONLY_USER, "notice-page")),
                post("/admin/system/notices/" + noticeId + "/withdraw")
                        .header("token", token(PAGE_ONLY_USER, "notice-page")),
                multipart("/admin/system/notices/images")
                        .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes()))
                        .header("token", token(PAGE_ONLY_USER, "notice-page"))}) {
            mockMvc.perform(write)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(205))
                    .andExpect(jsonPath("$.message").value("没有操作权限"));
        }

        // 页面 + add：只能新增，不能编辑/删除/发布。
        mockMvc.perform(createNotice(token(ADD_ONLY_USER, "notice-add"), "新增成功", 1, "<p>x</p>"))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(updateNotice(token(ADD_ONLY_USER, "notice-add"), noticeId))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish")
                        .header("token", token(ADD_ONLY_USER, "notice-add")))
                .andExpect(jsonPath("$.code").value(205));

        // 页面 + 四个按钮：全部放行。
        mockMvc.perform(updateNotice(token(FULL_USER, "notice-full"), noticeId))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish")
                        .header("token", token(FULL_USER, "notice-full")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/withdraw")
                        .header("token", token(FULL_USER, "notice-full")))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(delete("/admin/system/notices/" + noticeId)
                        .header("token", token(FULL_USER, "notice-full")))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void stateMachinePublishWithdrawRepublishAndEditRules() throws Exception {
        String admin = adminToken();
        Integer noticeId = createDraftDirectly(6403L, "状态机", 0);
        String adminName = jdbcTemplate.queryForObject(
                "SELECT name FROM sys_user WHERE id = 1", String.class);

        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish").header("token", admin))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.publisherId").value(1))
                .andExpect(jsonPath("$.data.publisherName").value(adminName));
        assertNotNull(jdbcTemplate.queryForObject(
                "SELECT publish_time FROM oa_notice WHERE id = ?", java.sql.Timestamp.class, noticeId));

        // 已发布不能再「发布」（状态机只允许草稿/已撤回 → 已发布）。
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish").header("token", admin))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告当前状态不可发布"));

        // 已发布可编辑，发布人快照与发布时间不变。
        java.sql.Timestamp publishedAt = jdbcTemplate.queryForObject(
                "SELECT publish_time FROM oa_notice WHERE id = ?", java.sql.Timestamp.class, noticeId);
        mockMvc.perform(put("/admin/system/notices/" + noticeId)
                        .header("token", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"已发布改标题\",\"type\":2,\"content\":\"<p>改</p>\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1))
                .andExpect(jsonPath("$.data.title").value("已发布改标题"))
                .andExpect(jsonPath("$.data.publisherName").value(adminName));
        assertEquals(publishedAt, jdbcTemplate.queryForObject(
                "SELECT publish_time FROM oa_notice WHERE id = ?", java.sql.Timestamp.class, noticeId));

        // 撤回 → 已撤回；再撤回是状态冲突；已撤回不可编辑。
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/withdraw").header("token", admin))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2));
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/withdraw").header("token", admin))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告当前状态不可撤回"));
        mockMvc.perform(updateNotice(admin, noticeId))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告当前状态不可编辑"));

        // 已撤回可再发布，刷新 publish_time。
        jdbcTemplate.update("UPDATE oa_notice SET publish_time = '2025-01-01 00:00:00' WHERE id = ?", noticeId);
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish").header("token", admin))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1));
        assertTrue(jdbcTemplate.queryForObject(
                        "SELECT publish_time FROM oa_notice WHERE id = ?", java.sql.Timestamp.class, noticeId)
                .after(java.sql.Timestamp.valueOf("2025-01-01 00:00:00")));

        // 任意状态可软删；删后写动作都按不存在处理。
        mockMvc.perform(delete("/admin/system/notices/" + noticeId).header("token", admin))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM oa_notice WHERE id = ?", Integer.class, noticeId));
        mockMvc.perform(delete("/admin/system/notices/" + noticeId).header("token", admin))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告不存在"));
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish").header("token", admin))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告不存在"));
        mockMvc.perform(get("/admin/system/notices/" + noticeId).header("token", admin))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    @Test
    void adminListFiltersAndExcludesDeleted() throws Exception {
        insertNotice(6410L, "年度公告", 2, 1, "2026-03-01 10:00:00", 0);
        insertNotice(6411L, "季度公告", 1, 1, "2026-03-02 10:00:00", 0);
        insertNotice(6412L, "年度草稿", 2, 0, "2026-03-03 10:00:00", 0);
        insertNotice(6413L, "已删公告", 2, 1, "2026-03-04 10:00:00", 1);
        insertNotice(6414L, "年度公告", 2, 1, "2026-03-01 10:00:00", 0);

        // 标题模糊 + 类型 + 状态；软删行永不出现。
        mockMvc.perform(get("/admin/system/notices")
                        .header("token", adminToken())
                        .param("title", "公告")
                        .param("type", "2")
                        .param("status", "1"))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(6410)))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(6414)))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItem(6413))));

        mockMvc.perform(get("/admin/system/notices")
                        .header("token", adminToken())
                        .param("status", "0"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(6412));

        // 稳定排序：同一 create_time 时 id DESC。
        mockMvc.perform(get("/admin/system/notices")
                        .header("token", adminToken())
                        .param("title", "年度公告"))
                .andExpect(jsonPath("$.data.records[0].id").value(6414))
                .andExpect(jsonPath("$.data.records[1].id").value(6410));
    }

    @Test
    void deleteIsImplicitWithdrawAndReceiptsSurvive() throws Exception {
        insertUser(305L, "notice-reader");
        insertNotice(6420L, "将删除", 1, 1, "2026-04-01 10:00:00", 0);
        String reader = token(305L, "notice-reader");

        mockMvc.perform(post("/admin/notices/6420/read").header("token", reader))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_notice_read WHERE notice_id = 6420 AND user_id = 305",
                Integer.class));

        mockMvc.perform(delete("/admin/system/notices/6420").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));

        // 读侧立即不可见，回执保留。
        mockMvc.perform(get("/admin/notices/6420").header("token", reader))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/admin/notices/unread-count").header("token", reader))
                .andExpect(jsonPath("$.data").value(0));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_notice_read WHERE notice_id = 6420 AND user_id = 305",
                Integer.class));
    }

    private MockHttpServletRequestBuilder createNotice(
            String token, String title, int type, String content) {
        return post("/admin/system/notices")
                .header("token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(noticeJson(title, type, content));
    }

    private MockHttpServletRequestBuilder updateNotice(String token, Integer noticeId) {
        return put("/admin/system/notices/" + noticeId)
                .header("token", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(noticeJson("改标题", 1, "<p>改</p>"));
    }

    private String noticeJson(String title, int type, String content) {
        return "{\"title\":\"" + escape(title) + "\",\"type\":" + type
                + ",\"content\":\"" + escape(content) + "\"}";
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private Integer createDraftDirectly(long id, String title, int status) {
        jdbcTemplate.update("INSERT INTO oa_notice (id, title, type, content, status, create_time, update_time, is_deleted) "
                + "VALUES (?, ?, 1, '<p>正文</p>', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)", id, title, status);
        return (int) id;
    }

    private void insertNotice(long id, String title, int type, int status, String createTime, int deleted) {
        jdbcTemplate.update("INSERT INTO oa_notice (id, title, type, content, status, create_time, update_time, is_deleted) "
                + "VALUES (?, ?, ?, '<p>x</p>', ?, ?, ?, ?)", id, title, type, status, createTime, createTime, deleted);
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin", 0L).token();
    }

    private String token(long userId, String username) {
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private void insertUser(long id, String username) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, 0)", id, username, username);
    }

    private void insertRole(long id, String code) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, is_deleted) VALUES (?, ?, ?, 0)",
                id, code, code);
    }

    private void insertUserRole(long userId, long roleId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                userId, roleId);
    }

    private void grantPage(long roleId) {
        Integer pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/notice' AND type = 1 AND is_deleted = 0",
                Integer.class);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, pageId);
    }

    private void grantButton(long roleId, String permission) {
        Integer buttonId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = ? AND type = 2 AND is_deleted = 0",
                Integer.class, permission);
        jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                roleId, buttonId);
    }

    private byte[] pngBytes() {
        return new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
    }

    private void cleanFixtures() {
        // 本类独占这份 H2 schema：接口新建的公告拿的是自增 id，不能只按夹具区间删。
        jdbcTemplate.update("DELETE FROM oa_notice_read");
        jdbcTemplate.update("DELETE FROM oa_notice_image");
        jdbcTemplate.update("DELETE FROM oa_notice");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 701 AND 799");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 301 AND 399");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 701 AND 799");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 301 AND 399");
        // 显式夹具 id 与接口自增 id 共用一张表：每例把序列钉回夹具区间起点。
        jdbcTemplate.execute("ALTER TABLE oa_notice ALTER COLUMN id RESTART WITH 6400");
    }
}
