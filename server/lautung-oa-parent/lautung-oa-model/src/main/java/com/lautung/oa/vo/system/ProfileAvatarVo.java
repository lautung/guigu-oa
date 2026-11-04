package com.lautung.oa.vo.system;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 头像上传响应（#254）：{@code url} 是已认证同源二进制端点
 * {@code /admin/system/profile/avatar/{userId}}，与 {@code /info.avatar} 同形状。
 */
@Data
@AllArgsConstructor
public class ProfileAvatarVo {

    private String url;
}
