package com.lautung.oa.notice;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.auth.LoginAuthContractTest;
import com.lautung.oa.common.jwt.JwtHelper;
import com.lautung.oa.notice.image.NoticeImageStore;
import com.lautung.oa.notice.service.NoticeImageService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.hamcrest.Matchers.matchesPattern;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 公告图片合同（ADR 0055，#218）：真实 Flyway + H2，对象存储用内存假实现替换
 * {@code S3NoticeImageStore}，不需要 MinIO。
 *
 * <p>钉住：5MB 上限与容器 multipart 配置的边界、Content-Type + 魔数（含 SVG 拒绝）、
 * 上传按钮点（add 或 edit，页面授予仍必需）、读权限（上传人始终可读自己的图片，
 * 其他人只读已绑定已发布公告）、保存时绑定/解绑重算、删公告清对象、孤儿清理。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_notice_image_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LoginAuthContractTest.InMemoryRedisConfig.class, NoticeImageContractTest.ImageStoreConfig.class})
class NoticeImageContractTest {

    private static final long UPLOADER_ID = 401L;
    private static final String UPLOADER = "notice-img-uploader";
    private static final long OTHER_ID = 402L;
    private static final String OTHER = "notice-image-other";
    private static final long EDITOR_ID = 403L;
    private static final String EDITOR = "notice-image-editor";
    private static final long PAGE_ONLY_ID = 404L;
    private static final String PAGE_ONLY = "notice-img-pageonly";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private NoticeImageService imageService;

    @Autowired
    private TestNoticeImageStore imageStore;

    @Value("${spring.servlet.multipart.max-file-size}")
    private DataSize multipartMaxFileSize;

    @BeforeEach
    void seedFixtures() {
        cleanFixtures();
        imageStore.clear();
        insertUser(UPLOADER_ID, UPLOADER);
        insertUser(OTHER_ID, OTHER);
        insertUser(EDITOR_ID, EDITOR);
        insertUser(PAGE_ONLY_ID, PAGE_ONLY);

        Integer pageId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE path = '/system/notice' AND type = 1", Integer.class);
        Integer addId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = 'system:notice:add' AND type = 2", Integer.class);
        Integer editId = jdbcTemplate.queryForObject(
                "SELECT id FROM sys_menu WHERE perms = 'system:notice:edit' AND type = 2", Integer.class);
        // 801 add-only / 802 edit-only / 803 仅页面授予（两个按钮都没有）。
        grantRole(801, "notice_image_add", UPLOADER_ID, pageId, addId);
        grantRole(802, "notice_image_edit", EDITOR_ID, pageId, editId);
        grantRole(803, "notice_image_page", PAGE_ONLY_ID, pageId);
    }

    private void grantRole(int roleId, String roleCode, long userId, Integer... menuIds) {
        jdbcTemplate.update("INSERT INTO sys_role (id, role_name, role_code, is_deleted) "
                + "VALUES (?, ?, ?, 0)", roleId, roleCode, roleCode);
        for (Integer menuId : menuIds) {
            jdbcTemplate.update("INSERT INTO sys_role_menu (role_id, menu_id, is_deleted) VALUES (?, ?, 0)",
                    roleId, menuId);
        }
        jdbcTemplate.update("INSERT INTO sys_user_role (user_id, role_id, is_deleted) VALUES (?, ?, 0)",
                userId, roleId);
    }

    @AfterEach
    void removeFixtures() {
        cleanFixtures();
        imageStore.clear();
    }

    @Test
    void uploadsValidImageAndReturnsSameOriginUrl() throws Exception {
        byte[] content = pngBytes(32);
        long imageId = upload(adminToken(), "a.png", "image/png", content);

        mockMvc.perform(multipart("/admin/system/notices/images")
                        .file(new MockMultipartFile("file", "b.png", "image/png", content))
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url", matchesPattern("^/admin/notices/images/\\d+$")));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT * FROM oa_notice_image WHERE id = ?", imageId);
        assertNull(row.get("notice_id"));
        assertEquals(1L, ((Number) row.get("uploader_id")).longValue());
        assertTrue(((String) row.get("object_key")).startsWith("notices/"),
                String.valueOf(row.get("object_key")));
        assertEquals("image/png", row.get("content_type"));
        assertEquals((long) content.length, ((Number) row.get("size")).longValue());
        assertArrayEquals(content, imageStore.get((String) row.get("object_key")));
    }

    @Test
    void rejectsSvgMagicMismatchUnsupportedTypeAndOversize() throws Exception {
        int rowsBefore = imageRowCount();

        // SVG：声明类型不在白名单。
        expectUploadCode("image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(),
                201, "图片类型不支持");
        // SVG：声明成 png，魔数识别不出来。
        expectUploadCode("image/png", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(),
                201, "图片类型不支持");
        // 声明 png、内容却是 jpeg：Content-Type 与魔数不一致。
        expectUploadCode("image/png", jpegBytes(), 201, "图片内容与类型不一致");
        // 5MB + 1 字节：超过公告图片上限（容器已放到 6MB，到达业务规则）。
        expectUploadCode("image/png", pngBytes((int) DataSize.ofMegabytes(5).toBytes() + 1),
                201, "图片超过 5 MB 限制");
        // 非图片类型。
        expectUploadCode("text/plain", "hello".getBytes(), 201, "图片类型不支持");

        assertEquals(rowsBefore, imageRowCount(), "被拒绝的上传不得留下元数据行");
        assertTrue(imageStore.keys().isEmpty(), "被拒绝的上传不得写对象");
    }

    @Test
    void multipartLimitConfigAllowsImagesBetweenOneAndFiveMegabytes() throws Exception {
        // 容器默认 1MB 会把 5MB 图片挡在 service 之前；配置必须高于业务上限（ADR 0055/#218）。
        assertTrue(multipartMaxFileSize.toBytes() > DataSize.ofMegabytes(5).toBytes(),
                "spring.servlet.multipart.max-file-size 必须大于公告图片的 5MB 业务上限");

        byte[] twoMegabytes = pngBytes((int) DataSize.ofMegabytes(2).toBytes());
        long imageId = upload(adminToken(), "big.png", "image/png", twoMegabytes);
        assertEquals(twoMegabytes.length, ((Number) jdbcTemplate.queryForObject(
                "SELECT size FROM oa_notice_image WHERE id = ?", Long.class, imageId)).intValue());
    }

    @Test
    void uploadAcceptsAddOrEditButtonButStillRequiresThePageGrant() throws Exception {
        // add-only：可以上传。
        expectUploadCode(token(UPLOADER_ID, UPLOADER), 200);
        // edit-only：编辑者用同一个编辑器，也可以上传。
        expectUploadCode(token(EDITOR_ID, EDITOR), 200);
        // 有页面授予但两个按钮都没有：205，且不留元数据行。
        expectUploadCode(token(PAGE_ONLY_ID, PAGE_ONLY), 205, "没有操作权限");
        assertEquals(2, imageRowCount(), "被拒绝的上传不得留下元数据行");
    }

    @Test
    void imageReadRequiresPublishedBindingOrOwnership() throws Exception {
        byte[] content = pngBytes(16);
        String uploaderToken = token(UPLOADER_ID, UPLOADER);
        long imageId = upload(uploaderToken, "a.png", "image/png", content);
        String url = "/admin/notices/images/" + imageId;

        // 未绑定：上传人可读，其他登录用户 205，匿名 401。
        mockMvc.perform(get(url).header("token", uploaderToken))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(result -> assertArrayEquals(content, result.getResponse().getContentAsByteArray()));

        mockMvc.perform(get(url).header("token", token(OTHER_ID, OTHER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(get(url))
                .andExpect(status().isUnauthorized());

        // 绑定到上传人的草稿：上传人仍可读（编辑器预览），其他人不可读。
        long noticeId = createNoticeWithImages(uploaderToken, "绑图公告", url);
        mockMvc.perform(get(url).header("token", uploaderToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(content, result.getResponse().getContentAsByteArray()));
        mockMvc.perform(get(url).header("token", token(OTHER_ID, OTHER)))
                .andExpect(jsonPath("$.code").value(205));

        // 发布后，任何登录用户可读。
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get(url).header("token", token(OTHER_ID, OTHER)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(result -> assertArrayEquals(content, result.getResponse().getContentAsByteArray()));

        // 撤回：其他登录用户读不到，上传人仍可读。
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/withdraw").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get(url).header("token", token(OTHER_ID, OTHER)))
                .andExpect(jsonPath("$.code").value(205));
        mockMvc.perform(get(url).header("token", uploaderToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(content, result.getResponse().getContentAsByteArray()));

        // 删除：图片行与对象一起消失，按不存在处理。
        mockMvc.perform(post("/admin/system/notices/" + noticeId + "/publish").header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));
        String objectKey = jdbcTemplate.queryForObject(
                "SELECT object_key FROM oa_notice_image WHERE id = ?", String.class, imageId);
        mockMvc.perform(delete("/admin/system/notices/" + noticeId).header("token", adminToken()))
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(0, imageRowCount());
        assertFalse(imageStore.keys().contains(objectKey), "删除公告必须删除绑定的图片对象");
        mockMvc.perform(get(url).header("token", uploaderToken))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("图片不存在"));
    }

    @Test
    void saveAndUpdateRebindImagesByContent() throws Exception {
        long first = upload(adminToken(), "a.png", "image/png", pngBytes(8));
        long second = upload(adminToken(), "b.png", "image/png", pngBytes(9));
        String firstUrl = "/admin/notices/images/" + first;
        String secondUrl = "/admin/notices/images/" + second;

        long noticeId = createNoticeWithImages(adminToken(), "绑定重算", firstUrl);
        assertEquals(noticeId, boundNoticeId(first));
        assertNull(boundNoticeId(second));

        // 换成第二张：第一张解除绑定、第二张绑定。
        mockMvc.perform(put("/admin/system/notices/" + noticeId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("绑定重算", 1, "<p><img src=\"" + secondUrl + "\"></p>")))
                .andExpect(jsonPath("$.code").value(200));
        assertNull(boundNoticeId(first));
        assertEquals(noticeId, boundNoticeId(second));

        // 正文清空：两张都解除绑定。
        mockMvc.perform(put("/admin/system/notices/" + noticeId)
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("绑定重算", 1, "<p>无图</p>")))
                .andExpect(jsonPath("$.code").value(200));
        assertNull(boundNoticeId(first));
        assertNull(boundNoticeId(second));
    }

    @Test
    void cannotBindAnotherUsersImageOrAnImageBoundElsewhere() throws Exception {
        // 上传者是 401（页面 + add），管理员不能把别人的图绑走。
        long foreign = upload(token(UPLOADER_ID, UPLOADER), "foreign.png", "image/png", pngBytes(8));
        String foreignUrl = "/admin/notices/images/" + foreign;

        mockMvc.perform(post("/admin/system/notices")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("借图", 1, "<p><img src=\"" + foreignUrl + "\"></p>")))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告图片不存在"));

        // 401 自己可以绑定；再被别的公告引用时按「已被使用」拒绝。
        long ownNotice = createNoticeWithImages(token(UPLOADER_ID, UPLOADER), "自己的图", foreignUrl);
        assertEquals(ownNotice, boundNoticeId(foreign));

        mockMvc.perform(post("/admin/system/notices")
                        .header("token", adminToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson("重复使用", 1, "<p><img src=\"" + foreignUrl + "\"></p>")))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("公告图片已被使用"));
    }

    @Test
    void orphanCleanupDeletesOnlyExpiredUnboundImages() throws Exception {
        long bound = upload(adminToken(), "bound.png", "image/png", pngBytes(8));
        long orphan = upload(adminToken(), "orphan.png", "image/png", pngBytes(9));
        String boundUrl = "/admin/notices/images/" + bound;
        long noticeId = createNoticeWithImages(adminToken(), "清理样本", boundUrl);

        String boundKey = objectKeyOf(bound);
        String orphanKey = objectKeyOf(orphan);
        // 两张都超过 24 小时：绑定的保留、未绑定的清理。
        jdbcTemplate.update("UPDATE oa_notice_image SET update_time = DATEADD('HOUR', -25, CURRENT_TIMESTAMP) "
                + "WHERE id IN (?, ?)", bound, orphan);
        imageService.cleanupOrphans();

        assertEquals(noticeId, boundNoticeId(bound));
        assertTrue(imageStore.keys().contains(boundKey));
        assertEquals(0, imageRowCountFor(orphan));
        assertFalse(imageStore.keys().contains(orphanKey));

        // 新上传的未绑定图片不受影响（不足 24 小时）。
        long fresh = upload(adminToken(), "fresh.png", "image/png", pngBytes(10));
        imageService.cleanupOrphans();
        assertEquals(1, imageRowCountFor(fresh));
        assertNotNull(objectKeyOf(fresh));
    }

    private void expectUploadCode(String contentType, byte[] content, int code, String message)
            throws Exception {
        mockMvc.perform(multipart("/admin/system/notices/images")
                        .file(new MockMultipartFile("file", "sample", contentType, content))
                        .header("token", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    private void expectUploadCode(String token, int code) throws Exception {
        mockMvc.perform(multipart("/admin/system/notices/images")
                        .file(new MockMultipartFile("file", "sample.png", "image/png", pngBytes(8)))
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void expectUploadCode(String token, int code, String message) throws Exception {
        mockMvc.perform(multipart("/admin/system/notices/images")
                        .file(new MockMultipartFile("file", "sample.png", "image/png", pngBytes(8)))
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    private long upload(String token, String fileName, String contentType, byte[] content) throws Exception {
        MvcResult result = mockMvc.perform(multipart("/admin/system/notices/images")
                        .file(new MockMultipartFile("file", fileName, contentType, content))
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private long createNoticeWithImages(String token, String title, String... imageUrls) throws Exception {
        StringBuilder content = new StringBuilder("<p>正文</p>");
        for (String imageUrl : imageUrls) {
            content.append("<img src=\"").append(imageUrl).append("\">");
        }
        MvcResult result = mockMvc.perform(post("/admin/system/notices")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(noticeJson(title, 1, content.toString())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.data.id");
        return id.longValue();
    }

    private String noticeJson(String title, int type, String content) {
        return "{\"title\":\"" + title + "\",\"type\":" + type
                + ",\"content\":\"" + content.replace("\"", "\\\"") + "\"}";
    }

    private Long boundNoticeId(long imageId) {
        return jdbcTemplate.queryForObject(
                "SELECT notice_id FROM oa_notice_image WHERE id = ?", Long.class, imageId);
    }

    private String objectKeyOf(long imageId) {
        return jdbcTemplate.queryForObject(
                "SELECT object_key FROM oa_notice_image WHERE id = ?", String.class, imageId);
    }

    private int imageRowCount() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM oa_notice_image", Integer.class);
    }

    private int imageRowCountFor(long imageId) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM oa_notice_image WHERE id = ?", Integer.class, imageId);
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

    private byte[] pngBytes(int size) {
        byte[] content = new byte[Math.max(size, 8)];
        System.arraycopy(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, 0, content, 0, 8);
        Arrays.fill(content, 8, content.length, (byte) 1);
        return content;
    }

    private byte[] jpegBytes() {
        return new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 1, 2, 3};
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM oa_notice_read");
        jdbcTemplate.update("DELETE FROM oa_notice_image");
        jdbcTemplate.update("DELETE FROM oa_notice");
        jdbcTemplate.update("DELETE FROM sys_role_menu WHERE role_id BETWEEN 801 AND 899");
        jdbcTemplate.update("DELETE FROM sys_user_role WHERE user_id BETWEEN 401 AND 499");
        jdbcTemplate.update("DELETE FROM sys_role WHERE id BETWEEN 801 AND 899");
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 401 AND 499");
        jdbcTemplate.execute("ALTER TABLE oa_notice ALTER COLUMN id RESTART WITH 6500");
    }

    @TestConfiguration
    static class ImageStoreConfig {

        @Bean
        @Primary
        TestNoticeImageStore testNoticeImageStore() {
            return new TestNoticeImageStore();
        }
    }

    static class TestNoticeImageStore implements NoticeImageStore {

        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String objectKey, byte[] content, String contentType) {
            objects.put(objectKey, content.clone());
        }

        @Override
        public byte[] get(String objectKey) {
            return objects.get(objectKey);
        }

        @Override
        public void delete(String objectKey) {
            objects.remove(objectKey);
        }

        void clear() {
            objects.clear();
        }

        java.util.Set<String> keys() {
            return objects.keySet();
        }
    }
}
