package com.lautung.oa.model.notice;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import lombok.Data;

import java.util.Date;

/**
 * 公告（ADR 0055）：全员广播 + 已读回执，不是待办。
 *
 * <p>三态 {@code 0 草稿 / 1 已发布 / 2 已撤回}。发布是显式动作；撤回后可再发布，
 * 再次发布刷新 {@code publish_time} 并重写发布人快照。删除是软删，等价于隐式撤回，
 * 不对读侧可见，也不清除回执。类型固定 {@code 1 通知 / 2 公告}，不建字典。
 */
@Data
@TableName("oa_notice")
public class Notice extends BaseEntity {

    public static final int STATUS_DRAFT = 0;
    public static final int STATUS_PUBLISHED = 1;
    public static final int STATUS_WITHDRAWN = 2;

    public static final int TYPE_NOTIFICATION = 1;
    public static final int TYPE_ANNOUNCEMENT = 2;

    @TableField("title")
    private String title;

    @TableField("type")
    private Integer type;

    /** 库里的正文是写入时消毒过的干净 HTML（ADR 0055），读侧不再二次消毒。 */
    @TableField("content")
    private String content;

    @TableField("status")
    private Integer status;

    /** 发布人快照：不在读时 join {@code sys_user}，管理员改名不重写历史公告。 */
    @TableField("publisher_id")
    private Long publisherId;

    @TableField("publisher_name")
    private String publisherName;

    @TableField("publish_time")
    private Date publishTime;
}
