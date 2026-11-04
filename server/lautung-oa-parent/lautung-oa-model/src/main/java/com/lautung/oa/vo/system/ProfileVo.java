package com.lautung.oa.vo.system;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 个人中心资料（#254）：用户名只读展示，姓名/手机可改；{@code avatar} 是已认证同源
 * 二进制端点的路径，没有头像时为空串（客户端用默认图兜底）。
 */
@Data
@AllArgsConstructor
public class ProfileVo {

    private String username;

    private String name;

    private String phone;

    private String avatar;
}
