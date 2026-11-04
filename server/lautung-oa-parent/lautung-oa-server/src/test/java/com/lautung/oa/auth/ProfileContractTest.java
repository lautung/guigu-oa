package com.lautung.oa.auth;

import com.jayway.jsonpath.JsonPath;
import com.lautung.oa.auth.profile.ProfileAvatarStore;
import com.lautung.oa.common.jwt.JwtHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.util.unit.DataSize;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 个人中心合同（#254，ADR 0062/0029）：真实 Flyway + H2，头像对象存储用内存假实现替换
 * {@code S3ProfileAvatarStore}，不需要 MinIO。
 *
 * <p>钉住：资料读写与用户名只读、姓名字段校验、头像类型/大小/覆盖式写入、头像读权限
 * （本人可读、他人不可读）、旧密码错误 201、改密后旧 access/refresh 立即 208、
 * {@code /info.avatar} 形状；个人中心登录即有，不是后台页（不设页面授予/按钮点）。
 */
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:guigu_oa_profile_contract;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=FALSE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;INIT=CREATE DOMAIN IF NOT EXISTS DATETIME AS TIMESTAMP")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({LoginAuthContractTest.InMemoryRedisConfig.class, ProfileContractTest.AvatarStoreConfig.class})
class ProfileContractTest {

    private static final long ALICE_ID = 501L;
    private static final String ALICE = "profile_alice";
    private static final long BOB_ID = 502L;
    private static final String BOB = "profile_bob";
    private static final String OLD_PASSWORD = "111111";
    private static final String NEW_PASSWORD = "654321";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtHelper jwtHelper;

    @Autowired
    private TestProfileAvatarStore avatarStore;

    @BeforeEach
    void seedUsers() {
        cleanFixtures();
        insertUser(ALICE_ID, ALICE, "爱丽丝", "13800001111");
        insertUser(BOB_ID, BOB, "鲍勃", "13800002222");
    }

    @AfterEach
    void removeFixtures() {
        cleanFixtures();
        avatarStore.clear();
    }

    @Test
    void readsAndUpdatesOwnNameAndPhoneWithoutChangingTheUsername() throws Exception {
        String token = token(ALICE_ID, ALICE);

        mockMvc.perform(get("/admin/system/profile").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value(ALICE))
                .andExpect(jsonPath("$.data.name").value("爱丽丝"))
                .andExpect(jsonPath("$.data.phone").value("13800001111"))
                .andExpect(jsonPath("$.data.avatar").value(""));

        // 用户名只读：请求体里塞 username 也不会改库。
        mockMvc.perform(put("/admin/system/profile")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"hacked\",\"name\":\"爱丽丝改名\",\"phone\":\"13900003333\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/admin/system/profile").header("token", token))
                .andExpect(jsonPath("$.data.username").value(ALICE))
                .andExpect(jsonPath("$.data.name").value("爱丽丝改名"))
                .andExpect(jsonPath("$.data.phone").value("13900003333"));

        Map<String, Object> alice = userRow(ALICE_ID);
        assertEquals(ALICE, alice.get("username"), "用户名只读，请求体里的 username 必须被忽略");
        assertEquals("爱丽丝改名", alice.get("name"));
        assertEquals("13900003333", alice.get("phone"));

        // 只改本人：另一行不受影响。
        Map<String, Object> bob = userRow(BOB_ID);
        assertEquals("鲍勃", bob.get("name"));
        assertEquals("13800002222", bob.get("phone"));
    }

    @Test
    void rejectsBlankAndOverlongProfileFieldsWithFieldMessages() throws Exception {
        String token = token(ALICE_ID, ALICE);

        mockMvc.perform(put("/admin/system/profile")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \",\"phone\":\"13800001111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("姓名不能为空"));

        mockMvc.perform(put("/admin/system/profile")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"" + "名".repeat(21) + "\",\"phone\":\"13800001111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("姓名过长"));

        mockMvc.perform(put("/admin/system/profile")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"爱丽丝\",\"phone\":\"" + "1".repeat(21) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("手机号过长"));

        // 校验不落库。
        assertEquals("爱丽丝", userRow(ALICE_ID).get("name"));
    }

    @Test
    void profileRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/admin/system/profile"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(put("/admin/system/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"匿名\",\"phone\":\"1\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(post("/admin/system/profile/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"111111\",\"newPassword\":\"654321\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(multipart("/admin/system/profile/avatar")
                        .file(new MockMultipartFile("file", "a.png", "image/png", pngBytes(8))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
    }

    @Test
    void changingPasswordRequiresTheOldOneAndRetiresEveryOldSession() throws Exception {
        String oldAccess = loginToken(ALICE, OLD_PASSWORD);
        String oldRefresh = JsonPath.read(loginBody(ALICE, OLD_PASSWORD), "$.data.refreshToken");
        String bobToken = token(BOB_ID, BOB);

        // 旧密码必须先对上：错误旧密码是业务失败 201，不推进版本、不作废会话、不写新哈希。
        String hashBefore = passwordHash(ALICE_ID);
        mockMvc.perform(post("/admin/system/profile/password")
                        .header("token", oldAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"000000\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("旧密码错误"));
        assertEquals(0L, tokenVersion(ALICE_ID), "旧密码错误不得推进令牌版本");
        assertEquals(hashBefore, passwordHash(ALICE_ID), "旧密码错误不得写新哈希");
        mockMvc.perform(get("/admin/system/index/info").header("token", oldAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(post("/admin/system/profile/password")
                        .header("token", oldAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"" + OLD_PASSWORD + "\",\"newPassword\":\"" + NEW_PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(1L, tokenVersion(ALICE_ID), "改密必须推进令牌版本");
        assertTrue(passwordEncoder.matches(NEW_PASSWORD, passwordHash(ALICE_ID)));

        // 改密前签发的 access/refresh 都立即 208；旧口令不能登录。
        mockMvc.perform(get("/admin/system/index/info").header("token", oldAccess))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(post("/admin/system/index/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"" + oldRefresh + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));
        mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + ALICE + "\",\"password\":\"" + OLD_PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));

        // 只影响本人：新口令签发的新令牌可用，其他人的令牌不受影响。
        mockMvc.perform(get("/admin/system/index/info")
                        .header("token", loginToken(ALICE, NEW_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
        mockMvc.perform(get("/admin/system/index/info").header("token", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        // 操作日志不回显明文密码。
        Map<String, Object> oper = jdbcTemplate.queryForMap(
                "SELECT * FROM sys_oper_log ORDER BY id DESC LIMIT 1");
        String param = String.valueOf(oper.get("oper_param"));
        assertFalse(param.contains(NEW_PASSWORD), "操作日志不得回显明文密码: " + param);
        assertTrue(param.contains("\"newPassword\":\"***\""), param);
    }

    @Test
    void rejectsBlankAndShortPasswordsWithFieldMessages() throws Exception {
        String token = token(ALICE_ID, ALICE);

        mockMvc.perform(post("/admin/system/profile/password")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newPassword\":\"654321\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("旧密码不能为空"));

        mockMvc.perform(post("/admin/system/profile/password")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"111111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("新密码不能为空"));

        mockMvc.perform(post("/admin/system/profile/password")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"oldPassword\":\"111111\",\"newPassword\":\"12345\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("密码长度必须在 6 到 100 位之间"));

        assertEquals(0L, tokenVersion(ALICE_ID), "校验失败不得推进令牌版本");
    }

    @Test
    void uploadsAnAvatarOverwritesTheSameObjectAndServesItBack() throws Exception {
        String token = token(ALICE_ID, ALICE);
        byte[] png = pngBytes(24);

        long first = uploadAvatar(token, "a.png", "image/png", png);
        assertEquals(ALICE_ID, first);
        assertEquals("avatar/" + ALICE_ID, headUrl(ALICE_ID));
        assertArrayEquals(png, avatarStore.get("avatar/" + ALICE_ID));

        mockMvc.perform(get("/admin/system/profile/avatar/{id}", ALICE_ID).header("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(result -> assertArrayEquals(png, result.getResponse().getContentAsByteArray()));

        mockMvc.perform(get("/admin/system/index/info").header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.avatar").value("/admin/system/profile/avatar/" + ALICE_ID));
        mockMvc.perform(get("/admin/system/profile").header("token", token))
                .andExpect(jsonPath("$.data.avatar").value("/admin/system/profile/avatar/" + ALICE_ID));

        // 覆盖式写入：仍是同一个对象键，内容与类型换成新的；不保留历史版本。
        byte[] jpeg = jpegBytes();
        uploadAvatar(token, "b.jpg", "image/jpeg", jpeg);
        assertEquals("avatar/" + ALICE_ID, headUrl(ALICE_ID));
        assertEquals(Set.of("avatar/" + ALICE_ID), avatarStore.keys());
        assertArrayEquals(jpeg, avatarStore.get("avatar/" + ALICE_ID));
        mockMvc.perform(get("/admin/system/profile/avatar/{id}", ALICE_ID).header("token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/jpeg"))
                .andExpect(result -> assertArrayEquals(jpeg, result.getResponse().getContentAsByteArray()));
    }

    @Test
    void rejectsUnsupportedOversizedOrMismatchedAvatars() throws Exception {
        String token = token(ALICE_ID, ALICE);
        int objectsBefore = avatarStore.keys().size();

        expectAvatarCode("image/svg+xml", "<svg xmlns=\"http://www.w3.org/2000/svg\"></svg>".getBytes(),
                201, "头像类型不支持");
        expectAvatarCode("image/gif", gifBytes(), 201, "头像类型不支持");
        expectAvatarCode("text/plain", "hello".getBytes(), 201, "头像类型不支持");
        // 声明 png、内容却是 jpeg：Content-Type 与魔数不一致。
        expectAvatarCode("image/png", jpegBytes(), 201, "头像内容与类型不一致");
        // 2MB + 1 字节：容器已放到 6MB，到达业务规则。
        expectAvatarCode("image/png", pngBytes((int) DataSize.ofMegabytes(2).toBytes() + 1),
                201, "头像超过 2 MB 限制");
        // 空文件。
        mockMvc.perform(multipart("/admin/system/profile/avatar")
                        .file(new MockMultipartFile("file", "empty.png", "image/png", new byte[0]))
                        .header("token", token))
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("头像不能为空"));

        assertNull(headUrl(ALICE_ID), "被拒绝的上传不得写 head_url");
        assertEquals(objectsBefore, avatarStore.keys().size(), "被拒绝的上传不得写对象");
    }

    @Test
    void avatarReadIsRestrictedToItsOwner() throws Exception {
        String aliceToken = token(ALICE_ID, ALICE);
        String bobToken = token(BOB_ID, BOB);
        byte[] alicePng = pngBytes(12);
        uploadAvatar(aliceToken, "a.png", "image/png", alicePng);

        // 本人可读。
        mockMvc.perform(get("/admin/system/profile/avatar/{id}", ALICE_ID).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertArrayEquals(alicePng, result.getResponse().getContentAsByteArray()));

        // 他人不可读；匿名不可读。
        mockMvc.perform(get("/admin/system/profile/avatar/{id}", ALICE_ID).header("token", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(get("/admin/system/profile/avatar/{id}", BOB_ID).header("token", aliceToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(205))
                .andExpect(jsonPath("$.message").value("没有操作权限"));
        mockMvc.perform(get("/admin/system/profile/avatar/{id}", ALICE_ID))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(208));

        // 没有头像的用户读自己是「头像不存在」，不是 500。
        mockMvc.perform(get("/admin/system/profile/avatar/{id}", BOB_ID).header("token", bobToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(201))
                .andExpect(jsonPath("$.message").value("头像不存在"));
    }

    @Test
    void profileUpdateNeverTouchesPasswordOrAvatarColumns() throws Exception {
        String token = token(ALICE_ID, ALICE);
        uploadAvatar(token, "a.png", "image/png", pngBytes(10));
        String hashBefore = passwordHash(ALICE_ID);

        mockMvc.perform(put("/admin/system/profile")
                        .header("token", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"改资料\",\"phone\":\"13900004444\",\"password\":\"leak\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        assertEquals(hashBefore, passwordHash(ALICE_ID), "资料编辑不得写 password 列");
        assertEquals("avatar/" + ALICE_ID, headUrl(ALICE_ID), "资料编辑不得清空头像");
        assertEquals(0L, tokenVersion(ALICE_ID), "资料编辑不得推进令牌版本");
        assertNotEquals("leak", passwordHash(ALICE_ID));
    }

    private void expectAvatarCode(String contentType, byte[] content, int code, String message)
            throws Exception {
        mockMvc.perform(multipart("/admin/system/profile/avatar")
                        .file(new MockMultipartFile("file", "sample", contentType, content))
                        .header("token", token(ALICE_ID, ALICE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.message").value(message));
    }

    private long uploadAvatar(String token, String fileName, String contentType, byte[] content)
            throws Exception {
        MvcResult result = mockMvc.perform(multipart("/admin/system/profile/avatar")
                        .file(new MockMultipartFile("file", fileName, contentType, content))
                        .header("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.url").value("/admin/system/profile/avatar/" + ALICE_ID))
                .andReturn();
        String url = JsonPath.read(result.getResponse().getContentAsString(), "$.data.url");
        return Long.parseLong(url.replaceAll(".*/", ""));
    }

    private String token(long userId, String username) {
        return jwtHelper.createTokenPair(userId, username, 0L).token();
    }

    private String loginToken(String username, String password) throws Exception {
        return JsonPath.read(loginBody(username, password), "$.data.token");
    }

    private String loginBody(String username, String password) throws Exception {
        return mockMvc.perform(post("/admin/system/index/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private void insertUser(long id, String username, String name, String phone) {
        jdbcTemplate.update("INSERT INTO sys_user (id, username, password, name, phone, status, is_deleted) "
                        + "VALUES (?, ?, ?, ?, ?, 1, 0)",
                id, username, passwordEncoder.encode(OLD_PASSWORD), name, phone);
    }

    private Map<String, Object> userRow(long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM sys_user WHERE id = ?", id);
    }

    private String passwordHash(long id) {
        return jdbcTemplate.queryForObject("SELECT password FROM sys_user WHERE id = ?", String.class, id);
    }

    private String headUrl(long id) {
        return jdbcTemplate.queryForObject("SELECT head_url FROM sys_user WHERE id = ?", String.class, id);
    }

    private long tokenVersion(long id) {
        Long version = jdbcTemplate.queryForObject("SELECT token_version FROM sys_user WHERE id = ?", Long.class, id);
        assertTrue(version != null);
        return version;
    }

    private void cleanFixtures() {
        jdbcTemplate.update("DELETE FROM sys_oper_log");
        jdbcTemplate.update("DELETE FROM sys_login_log WHERE username IN (?, ?)", ALICE, BOB);
        jdbcTemplate.update("DELETE FROM sys_user WHERE id BETWEEN 501 AND 599");
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

    private byte[] gifBytes() {
        return new byte[]{'G', 'I', 'F', '8', '9', 'a', 1, 2};
    }

    @TestConfiguration
    static class AvatarStoreConfig {

        @Bean
        @Primary
        TestProfileAvatarStore testProfileAvatarStore() {
            return new TestProfileAvatarStore();
        }
    }

    static class TestProfileAvatarStore implements ProfileAvatarStore {

        private final Map<String, byte[]> objects = new ConcurrentHashMap<>();

        @Override
        public void put(String objectKey, byte[] content, String contentType) {
            objects.put(objectKey, content.clone());
        }

        @Override
        public byte[] get(String objectKey) {
            return objects.get(objectKey);
        }

        void clear() {
            objects.clear();
        }

        Set<String> keys() {
            return objects.keySet();
        }
    }
}
