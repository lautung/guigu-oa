package com.lautung.oa.vo.notice;

import lombok.Data;

import java.util.Date;

/**
 * 读侧列表行（feed / unread / 弹层）：只带展示字段，**永不带正文**（ADR 0055）。
 * {@code read} 是当前用户是否已有已读回执。
 */
@Data
public class NoticeFeedItemVo {

    private Long id;

    private String title;

    /** 1 通知 / 2 公告。 */
    private Integer type;

    private String publisherName;

    private Date publishTime;

    private Boolean read;
}
