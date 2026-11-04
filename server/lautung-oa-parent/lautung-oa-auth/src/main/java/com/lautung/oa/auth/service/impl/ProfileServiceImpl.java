package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.profile.ProfileAvatarContent;
import com.lautung.oa.auth.profile.ProfileAvatarPaths;
import com.lautung.oa.auth.profile.ProfileAvatarStore;
import com.lautung.oa.auth.profile.ProfileAvatarTypes;
import com.lautung.oa.auth.service.ProfileService;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.system.SysUser;
import com.lautung.oa.vo.system.ProfileAvatarVo;
import com.lautung.oa.vo.system.ProfileUpdateRequest;
import com.lautung.oa.vo.system.ProfileVo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Objects;

/**
 * 个人中心实现（#254）：头像走现有私有桶的固定键覆盖，改密复用 {@link SysUserService#changePassword}
 * 的同一原子语句（新哈希与 token_version + 1 一起落库，ADR 0029）。
 */
@Service
public class ProfileServiceImpl implements ProfileService {

    private static final Logger log = LoggerFactory.getLogger(ProfileServiceImpl.class);

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;
    private final ProfileAvatarStore avatarStore;

    public ProfileServiceImpl(
            SysUserService sysUserService,
            PasswordEncoder passwordEncoder,
            ProfileAvatarStore avatarStore) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
        this.avatarStore = avatarStore;
    }

    @Override
    public ProfileVo find(Long userId) {
        SysUser user = requireUser(userId);
        return new ProfileVo(
                user.getUsername(), user.getName(), user.getPhone(), ProfileService.avatarUrl(user));
    }

    @Override
    public void update(Long userId, ProfileUpdateRequest request) {
        // 只挑三个字段落库：用户名只读，密码/头像/状态不随资料编辑改变。
        SysUser user = new SysUser();
        user.setId(userId);
        user.setName(request.getName());
        user.setPhone(request.getPhone());
        if (!sysUserService.updateById(user)) {
            throw new BusinessException(201, "用户不存在");
        }
    }

    @Override
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        SysUser user = requireUser(userId);
        if (!passwordEncoder.matches(oldPassword, user.getPassword())) {
            throw new BusinessException(201, "旧密码错误");
        }
        // 与管理员重置密码共用同一语句：新哈希与 token_version + 1 同一条 UPDATE。
        sysUserService.changePassword(userId, passwordEncoder.encode(newPassword));
    }

    @Override
    public ProfileAvatarVo uploadAvatar(Long userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(201, "头像不能为空");
        }
        if (file.getSize() > ProfileAvatarTypes.MAX_BYTES) {
            throw new BusinessException(201, "头像超过 2 MB 限制");
        }
        String declared = ProfileAvatarTypes.normalize(file.getContentType());
        if (!ProfileAvatarTypes.isAllowed(declared)) {
            throw new BusinessException(201, "头像类型不支持");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new BusinessException(201, "头像读取失败");
        }
        if (content.length == 0) {
            throw new BusinessException(201, "头像不能为空");
        }
        if (content.length > ProfileAvatarTypes.MAX_BYTES) {
            throw new BusinessException(201, "头像超过 2 MB 限制");
        }
        String detected = ProfileAvatarTypes.detect(content);
        if (detected == null) {
            // 含 SVG/GIF：声明什么类型都不放行，魔数识别不出来就是拒绝。
            throw new BusinessException(201, "头像类型不支持");
        }
        if (!detected.equals(declared)) {
            throw new BusinessException(201, "头像内容与类型不一致");
        }

        String objectKey = ProfileAvatarPaths.objectKey(userId);
        avatarStore.put(objectKey, content, detected);
        if (!sysUserService.updateHeadUrl(userId, objectKey)) {
            throw new BusinessException(201, "用户不存在");
        }
        return new ProfileAvatarVo(ProfileAvatarPaths.readPath(userId));
    }

    @Override
    public ProfileAvatarContent readAvatar(Long currentUserId, Long targetUserId) {
        if (targetUserId == null || !Objects.equals(currentUserId, targetUserId)) {
            throw new BusinessException(205, "没有操作权限");
        }
        SysUser user = requireUser(currentUserId);
        if (user.getHeadUrl() == null || user.getHeadUrl().isBlank()) {
            throw new BusinessException(201, "头像不存在");
        }
        byte[] content;
        try {
            content = avatarStore.get(user.getHeadUrl());
        } catch (RuntimeException exception) {
            log.warn("Unable to read profile avatar user id={}", currentUserId);
            content = null;
        }
        if (content == null || content.length == 0) {
            throw new BusinessException(201, "头像不存在");
        }
        String contentType = ProfileAvatarTypes.detect(content);
        if (contentType == null) {
            throw new BusinessException(201, "头像不存在");
        }
        return new ProfileAvatarContent(content, contentType);
    }

    private SysUser requireUser(Long userId) {
        SysUser user = userId == null ? null : sysUserService.getById(userId);
        if (user == null) {
            throw new BusinessException(201, "用户不存在");
        }
        return user;
    }
}
