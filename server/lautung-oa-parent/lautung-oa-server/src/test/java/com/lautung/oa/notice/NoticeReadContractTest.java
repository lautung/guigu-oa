package com.lautung.oa.notice;

import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.notice.service.NoticeService;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公告读侧 HTTP 合同（ADR 0055，#218）：真实 Flyway（含 V116）+ H2，钉住
 * feed/unread/unread-count/detail/read 的形状、排序、不可见性与回执幂等。
 *
 * <p>读侧是内置面：这些用例的用户没有任何角色授予，仍然全部可读（ADR 0056）；
 * 撤回/软删对读侧立即不可见，但回执保留、重新发布不重置。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_notice_read_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(LoginAuthContractTest.InMemoryRedisConfig.class)
class NoticeReadContractTest {

    private static final long READER_ID = 201L;
    private static final long OTHER_ID = 202L;
    private static final String READER = "notice-reader";
    private static final String OTHER = "notice-other";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private NoticeService noticeService;

    @BeforeEach
    void seedFixtures() {
        cleanFixtures();
        insertUser(READER_ID, READER, "读者甲");
        insertUser(OTHER_ID, OTHER, "读者乙");

        insertNotice(6101L, "已发布一", 1, "<p>一</p>", 1,
                "2026-01-01 09:00:00", 0);
        insertNotice(6102L, "已发布二", 2, "<p>二</p>", 1,
                "2026-01-02 09:00:00", 0);
        insertNotice(6103L, "已发布三", 1, "<p>三</p>", 1,
                "2026-01-02 09:00:00", 0);
        insertNotice(6104L, "已撤回", 1, "<p>撤回</p>", 2,
                "2026-01-03 09:00:00", 0);
        insertNotice(6105L, "草稿", 1, "<p>草稿</p>", 0,
                null, 0);
        insertNotice(6106L, "已删除", 1, "<p>删除</p>", 1,
                "2026-01-04 09:00:00", 1);

        // 读者甲读过「已发布二」。
        jdbcTemplate.update("INSERT INTO oa_notice_read (notice_id, user_id, is_deleted) VALUES (6102, ?, 0)",
                READER_ID);
    }

    @AfterEach
    void removeFixtures() {
        cleanFixtures();
    }

    @Test
    void feedCarriesPagedShapeStableOrderReadFlagsAndNoContent() throws Exception {
        mockMvc.perform(feed(1L, 2, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.current").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records", hasSize(2)))
                // publish_time 相同（6102/6103）时 id DESC 决定次序。
                .andExpect(jsonPath("$.data.records[0].id").value(6103))
                .andExpect(jsonPath("$.data.records[1].id").value(6102))
                .andExpect(jsonPath("$.data.records[0].read").value(false))
                .andExpect(jsonPath("$.data.records[1].read").value(true))
                .andExpect(jsonPath("$.data.records[0].title").value("已发布三"))
                .andExpect(jsonPath("$.data.records[0].type").value(1))
                .andExpect(jsonPath("$.data.records[0].publisherName").value("管理员"))
                .andExpect(jsonPath("$.data.records[0].publishTime").value("2026-01-02 09:00:00"))
                // 列表永不返回正文。
                .andExpect(jsonPath("$.data.records[0].content").doesNotExist())
                .andExpect(jsonPath("$.data.records[0].status").doesNotExist());

        mockMvc.perform(feed(2L, 2, false))
                .andExpect(jsonPath("$.data.total").value(3))
                .andExpect(jsonPath("$.data.records", hasSize(1)))
                .andExpect(jsonPath("$.data.records[0].id").value(6101));

        // 分页窗口沿用 PageWindow：上限 500，越界 201。
        mockMvc.perform(feed(1L, 501, false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("每页记录数必须在 1 到 500 之间"));
    }

    @Test
    void unreadOnlyUnreadPopoverAndCountIgnoreWithdrawnDeletedAndRead() throws Exception {
        mockMvc.perform(feed(1L, 20, true))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(6103)))
                .andExpect(jsonPath("$.data.records[*].id", hasItem(6101)))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItem(6102))))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItem(6104))))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItem(6106))));

        mockMvc.perform(get("/admin/notices/unread").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].id").value(6103))
                .andExpect(jsonPath("$.data[1].id").value(6101))
                .andExpect(jsonPath("$.data[0].content").doesNotExist());

        mockMvc.perform(get("/admin/notices/unread-count").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value(2));

        // 全部读完后角标归零（回执只驱动未读，不是待办）。
        mockMvc.perform(post("/admin/notices/6101/read").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/notices/6103/read").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/notices/unread-count").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data").value(0));
        mockMvc.perform(get("/admin/notices/unread").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void unreadPopoverIsCappedAtFive() throws Exception {
        for (long id = 6200L; id < 6208L; id++) {
            insertNotice(id, "批量" + id, 1, "<p>x</p>", 1, "2026-02-01 09:00:00", 0);
        }

        mockMvc.perform(get("/admin/notices/unread").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data", hasSize(5)))
                .andExpect(jsonPath("$.data[0].id").value(6207))
                .andExpect(jsonPath("$.data[4].id").value(6203));
    }

    @Test
    void detailIsPublishedOnlyAndReceiptIsIdempotent() throws Exception {
        mockMvc.perform(get("/admin/notices/6101").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value(6101))
                .andExpect(jsonPath("$.data.title").value("已发布一"))
                .andExpect(jsonPath("$.data.type").value(1))
                .andExpect(jsonPath("$.data.content").value("<p>一</p>"))
                .andExpect(jsonPath("$.data.publisherId").value(1))
                .andExpect(jsonPath("$.data.publisherName").value("管理员"))
                .andExpect(jsonPath("$.data.publishTime").value("2026-01-01 09:00:00"))
                .andExpect(jsonPath("$.data.read").value(false));

        // 重复打开不产生第二行，第二次详情看到 read=true。
        mockMvc.perform(post("/admin/notices/6101/read").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(post("/admin/notices/6101/read").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, receiptCount(6101L, READER_ID));

        mockMvc.perform(get("/admin/notices/6101").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data.read").value(true));

        // 撤回/草稿/软删/不存在：读侧按不存在处理（没有 data）。
        for (long id : new long[]{6104L, 6105L, 6106L, 999999L}) {
            mockMvc.perform(get("/admin/notices/" + id).header("token", token(READER_ID, READER)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data").doesNotExist());
        }

        mockMvc.perform(post("/admin/notices/6104/read").header("token", token(READER_ID, READER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告不存在"));
    }

    @Test
    void withdrawHidesImmediatelyAndRepublishKeepsReceipts() throws Exception {
        mockMvc.perform(post("/admin/notices/6101/read").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(1, receiptCount(6101L, READER_ID));

        mockMvc.perform(post("/admin/system/notices/6101/withdraw").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(2));

        // 撤回后：feed/详情/未读计数立即看不到；回执行仍在。
        mockMvc.perform(feed(1L, 20, false))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.records[*].id", not(hasItem(6101))));
        mockMvc.perform(get("/admin/notices/6101").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data").doesNotExist());
        mockMvc.perform(get("/admin/notices/unread-count").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data").value(1));
        assertEquals(1, receiptCount(6101L, READER_ID));

        // 重新发布：刷新 publish_time，回执不重置（读者甲仍读不到这条未读）。
        jdbcTemplate.update("UPDATE oa_notice SET publish_time = '2025-12-01 09:00:00' WHERE id = 6101");
        mockMvc.perform(post("/admin/system/notices/6101/publish").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value(1));
        assertTrue(jdbcTemplate.queryForObject(
                "SELECT publish_time FROM oa_notice WHERE id = 6101", java.sql.Timestamp.class)
                .after(java.sql.Timestamp.valueOf("2025-12-01 09:00:00")));

        mockMvc.perform(get("/admin/notices/6101").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data.read").value(true));
        mockMvc.perform(get("/admin/notices/unread-count").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data").value(1));
        assertEquals(1, receiptCount(6101L, READER_ID));
    }

    @Test
    void editingContentDoesNotResetReceipts() throws Exception {
        mockMvc.perform(post("/admin/notices/6101/read").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(put("/admin/system/notices/6101")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"改过的标题\",\"type\":2,\"content\":\"<p>改过的正文</p>\"}"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.title").value("改过的标题"));

        mockMvc.perform(get("/admin/notices/6101").header("token", token(READER_ID, READER)))
                .andExpect(jsonPath("$.data.title").value("改过的标题"))
                .andExpect(jsonPath("$.data.content").value("<p>改过的正文</p>"))
                .andExpect(jsonPath("$.data.read").value(true));
        assertEquals(1, receiptCount(6101L, READER_ID));
    }

    @Test
    void concurrentReadWritesExactlyOneReceipt() throws Exception {
        int attempts = 8;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(attempts);
        List<Throwable> failures = new ArrayList<>();
        try {
            for (int index = 0; index < attempts; index++) {
                pool.submit(() -> {
                    try {
                        start.await();
                        noticeService.markRead(OTHER_ID, 6101L);
                    } catch (Throwable error) {
                        synchronized (failures) {
                            failures.add(error);
                        }
                    } finally {
                        done.countDown();
                    }
                });
            }
            start.countDown();
            assertTrue(done.await(20, TimeUnit.SECONDS), "并发回执写入超时");
        } finally {
            pool.shutdownNow();
        }

        assertTrue(failures.isEmpty(), "并发重复打开不得失败: " + failures);
        assertEquals(1, receiptCount(6101L, OTHER_ID),
                "唯一键 (notice_id,user_id) 必须只留一行");
    }

    @Test
    void readSideNeedsNoPageGrant() throws Exception {
        // 读者与 OTHER 都没有任何角色授予；读侧仍 200（不是 205）。
        mockMvc.perform(feed(1L, 20, false))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/notices/unread-count").header("token", token(OTHER_ID, OTHER)))
                .andExpect(jsonPath("$.code").value(200));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sys_user_role WHERE user_id IN (201,202)", Integer.class));
    }

    private MockHttpServletRequestBuilder feed(long page, long limit, boolean unreadOnly) {
        return get("/admin/notices/feed")
                .param("page", String.valueOf(page))
                .param("limit", String.valueOf(limit))
                .param("unreadOnly", String.valueOf(unreadOnly))
                .header("token", token(READER_ID, READER));
    }

    private String adminToken() {
        return jwtHelper.createTokenPair(1L, "admin", 0L).token();
    }

    private String token(long userId, String username) {
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private int receiptCount(long noticeId, long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_notice_read WHERE notice_id = ? AND user_id = ?",
                Integer.class, noticeId, userId);
    }

    private void insertUser(long id, String username, String name) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, status, is_deleted) "
                + "VALUES (?, ?, '', ?, 1, 0)", id, username, name);
    }

    private void insertNotice(long id, String title, int type, String content, int status,
                              String publishTime, int deleted) {
        jdbcTemplate.update("INSERT INTO oa_notice (id, title, type, content, status, publisher_id, "
                        + "publisher_name, publish_time, create_time, update_time, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, '管理员', ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, ?)",
                id, title, type, content, status, publishTime, deleted);
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM oa_notice_read WHERE notice_id BETWEEN 6100 AND 6299");
        jdbcTemplate.update("DELETE FROM oa_notice WHERE id BETWEEN 6100 AND 6299");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id IN (201, 202)");
    }
}
