package com.lautung.oa.auth.service;

import com.lautung.oa.auth.profile.ProfileAvatarContent;
import com.lautung.oa.auth.profile.ProfileAvatarPaths;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.system.ProfileAvatarVo;
import com.lautung.oa.vo.system.ProfileUpdateRequest;
import com.lautung.oa.vo.system.ProfileVo;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心（#254）：本人资料读写、头像上传/读取与改密。登录即有，不参与后台授予。
 */
public interface ProfileService {

    ProfileVo find(Long userId);

    /** 只写姓名/手机；用户名/密码/头像列不在这里出现。 */
    void update(Long userId, ProfileUpdateRequest request);

    /** 改密必须先验旧密码（ADR 0029）；成功后推进令牌版本，该用户全部会话失效。 */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /** jpeg/png/webp、≤2MB，固定键 {@code avatar/{userId}} 覆盖式写入。 */
    ProfileAvatarVo uploadAvatar(Long userId, MultipartFile file);

    /** 本人可读、他人不可读；没有头像或对象缺失按「头像不存在」。 */
    ProfileAvatarContent readAvatar(Long currentUserId, Long targetUserId);

    /** 资料与 {@code /info.avatar} 共用的同源读端点路径；没有头像时为空串。 */
    static String avatarUrl(SysUser user) {
        return user == null || user.getHeadUrl() == null || user.getHeadUrl().isBlank()
                ? ""
                : ProfileAvatarPaths.readPath(user.getId());
    }
}
