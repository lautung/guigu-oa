package com.lautung.oa.vo.notice;

import lombok.Data;

import java.util.Date;

/**
 * 管理侧详情/写后回读：编辑页需要草稿正文（#220），所以本形状带 {@code content}，
 * 与列表形状分开。
 */
@Data
public class NoticeAdminDetailVo {

    private Long id;

    private String title;

    private Integer type;

    private String content;

    private Integer status;

    private Long publisherId;

    private String publisherName;

    private Date publishTime;

    private Date createTime;

    private Date updateTime;
}
