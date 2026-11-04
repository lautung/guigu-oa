package com.lautung.oa.auth.profile;

/**
 * 头像对象键与同源读端点的唯一构造点（#254）：{@code /info.avatar}、资料读与上传响应
 * 都从这里取路径，避免三处各拼一份。
 */
public final class ProfileAvatarPaths {

    /** 现有私有附件桶里的前缀；固定键覆盖式写入，不保留历史版本。 */
    public static final String OBJECT_PREFIX = "avatar/";

    private ProfileAvatarPaths() {
    }

    public static String objectKey(Long userId) {
        return OBJECT_PREFIX + userId;
    }

    public static String readPath(Long userId) {
        return "/admin/system/profile/avatar/" + userId;
    }
}
