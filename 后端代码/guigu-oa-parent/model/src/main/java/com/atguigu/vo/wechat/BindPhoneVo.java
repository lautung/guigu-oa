package com.atguigu.vo.wechat;

import com.baomidou.mybatisplus.annotation.TableField;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class BindPhoneVo {

    @Schema(description = "手机")
    private String phone;

    @Schema(description = "openId")
    private String openId;
}
