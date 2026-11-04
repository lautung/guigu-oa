package com.lautung.oa.auth.controller;

import com.lautung.oa.auth.profile.ProfileAvatarContent;
import com.lautung.oa.auth.service.ProfileService;
import com.lautung.oa.common.result.Result;
import com.lautung.oa.framework.operlog.BusinessType;
import com.lautung.oa.framework.operlog.OperLog;
import com.lautung.oa.security.custom.LoginUserInfoHelper;
import com.lautung.oa.vo.system.ProfileAvatarVo;
import com.lautung.oa.vo.system.ProfilePasswordRequest;
import com.lautung.oa.vo.system.ProfileUpdateRequest;
import com.lautung.oa.vo.system.ProfileVo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 个人中心（#254）：本人资料、头像与改密。登录即有，不是后台页——不设页面授予、
 * 不设按钮点，因而不在 backoffice gate 合同里。
 */
@Tag(name = "个人中心")
@RestController
@RequestMapping("/admin/system/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @Operation(summary = "本人资料（用户名只读）")
    @GetMapping
    public Result<ProfileVo> profile() {
        return Result.ok(profileService.find(currentUserId()));
    }

    @Operation(summary = "更新本人姓名/手机")
    @OperLog(title = "个人中心·修改资料", businessType = BusinessType.UPDATE)
    @PutMapping
    public Result update(@Valid @RequestBody ProfileUpdateRequest request) {
        profileService.update(currentUserId(), request);
        return Result.ok();
    }

    @Operation(summary = "修改密码（必须验旧密码）")
    @OperLog(title = "个人中心·修改密码", businessType = BusinessType.UPDATE)
    @PostMapping("password")
    public Result changePassword(@Valid @RequestBody ProfilePasswordRequest request) {
        profileService.changePassword(
                currentUserId(), request.getOldPassword(), request.getNewPassword());
        return Result.ok();
    }

    @Operation(summary = "上传头像（jpeg/png/webp，≤2MB，覆盖式写入）")
    @OperLog(title = "个人中心·上传头像", businessType = BusinessType.CREATE)
    @PostMapping("avatar")
    public Result<ProfileAvatarVo> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return Result.ok(profileService.uploadAvatar(currentUserId(), file));
    }

    @Operation(summary = "取回本人头像（同源二进制）")
    @GetMapping("avatar/{userId}")
    public ResponseEntity<byte[]> avatar(@PathVariable Long userId) {
        ProfileAvatarContent avatar = profileService.readAvatar(currentUserId(), userId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .header("X-Content-Type-Options", "nosniff")
                .body(avatar.content());
    }

    private Long currentUserId() {
        return LoginUserInfoHelper.getUserId();
    }
}
