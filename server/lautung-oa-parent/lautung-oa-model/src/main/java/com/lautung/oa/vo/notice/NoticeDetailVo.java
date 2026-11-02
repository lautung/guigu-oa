package com.lautung.oa.vo.notice;

import lombok.Data;

import java.util.Date;

/**
 * 读侧详情（{@code GET /admin/notices/{id}}）：已发布且未删除才有数据，正文是写入时
 * 消毒过的 HTML。{@code read} 是本次读取之前的状态；写回执由显式的
 * {@code POST /admin/notices/{id}/read} 负责。
 */
@Data
public class NoticeDetailVo {

    private Long id;

    private String title;

    private Integer type;

    private String content;

    private Long publisherId;

    private String publisherName;

    private Date publishTime;

    private Boolean read;
}
