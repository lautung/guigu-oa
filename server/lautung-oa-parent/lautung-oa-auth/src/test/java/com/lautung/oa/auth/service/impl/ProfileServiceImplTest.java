package com.lautung.oa.auth.service.impl;

import com.lautung.oa.auth.profile.ProfileAvatarStore;
import com.lautung.oa.auth.service.SysUserService;
import com.lautung.oa.framework.config.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 头像上传顺序（PR #260 审查）：用户不存在时先拒绝，不覆盖对象。
 */
class ProfileServiceImplTest {

    private static final byte[] PNG = {
            (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x01};

    private final SysUserService sysUserService = mock(SysUserService.class);

    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    private final ProfileAvatarStore avatarStore = mock(ProfileAvatarStore.class);

    private final ProfileServiceImpl service = new ProfileServiceImpl(
            sysUserService, passwordEncoder, avatarStore);

    @Test
    void missingUserIsRejectedBeforeTheObjectIsWritten() {
        when(sysUserService.getById(9L)).thenReturn(null);
        MockMultipartFile file = new MockMultipartFile("file", "a.png", "image/png", PNG);

        BusinessException error = assertThrows(BusinessException.class,
                () -> service.uploadAvatar(9L, file));

        assertEquals(201, error.getCode());
        assertEquals("用户不存在", error.getMsg());
        verify(avatarStore, never()).put(anyString(), any(byte[].class), anyString());
        verify(sysUserService, never()).updateHeadUrl(any(), anyString());
    }
}
