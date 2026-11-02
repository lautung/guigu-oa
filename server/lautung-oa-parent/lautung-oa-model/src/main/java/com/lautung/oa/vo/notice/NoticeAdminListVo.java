package com.lautung.oa.vo.notice;

import lombok.Data;

import java.util.Date;

/**
 * 管理侧列表行：含三态与发布人快照，**不带正文**（列表永不返回正文，ADR 0055）。
 */
@Data
public class NoticeAdminListVo {

    private Long id;

    private String title;

    private Integer type;

    /** 0 草稿 / 1 已发布 / 2 已撤回。 */
    private Integer status;

    private Long publisherId;

    private String publisherName;

    private Date publishTime;

    private Date createTime;

    private Date updateTime;
}
