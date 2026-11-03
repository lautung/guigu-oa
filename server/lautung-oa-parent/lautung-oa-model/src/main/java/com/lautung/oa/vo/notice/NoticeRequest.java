package com.lautung.oa.vo.notice;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 公告写请求（ADR 0034/0055）：新建与更新共用，{@code id} 走路径，不在请求体里。
 * 长度上限对齐列宽：标题 100、正文 100000（字符数，图片只存 URL）。
 */
@Data
public class NoticeRequest {

    @NotBlank(message = "公告标题不能为空")
    @Size(max = 100, message = "公告标题过长")
    private String title;

    @NotNull(message = "公告类型不能为空")
    @Min(value = 1, message = "公告类型无效")
    @Max(value = 2, message = "公告类型无效")
    private Integer type;

    @Size(max = 100000, message = "公告正文过长")
    private String content;
}
