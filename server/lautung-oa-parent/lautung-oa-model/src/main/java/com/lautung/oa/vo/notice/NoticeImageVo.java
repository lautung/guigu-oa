package com.lautung.oa.vo.notice;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 图片上传响应：{@code url} 是正文里唯一允许的图片地址形状
 * {@code /admin/notices/images/{id}}（ADR 0055）。
 */
@Data
@AllArgsConstructor
public class NoticeImageVo {

    private Long id;

    private String url;
}
