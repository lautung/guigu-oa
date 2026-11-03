package com.lautung.oa.model.notice;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.lautung.oa.model.base.BaseEntity;
import lombok.Data;

/**
 * 公告已读回执（ADR 0055）：唯一键 {@code (notice_id, user_id)}，打开详情即写、幂等。
 *
 * <p>回执只驱动未读角标，不是待办，也没有读者名单/已读人数。撤回或删除公告都不清回执；
 * 重新发布时此前读过的人仍是已读，编辑正文也不重置。
 */
@Data
@TableName("oa_notice_read")
public class NoticeRead extends BaseEntity {

    @TableField("notice_id")
    private Long noticeId;

    @TableField("user_id")
    private Long userId;
}
