package com.lautung.oa.model.notice;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;

/**
 * 公告图片元数据（ADR 0055）。对象在现有私有附件桶的 {@code notices/} 前缀下，元数据只
 * 记对象键与展示属性；{@code notice_id} 可空（上传后尚未绑定），未绑定满 24 小时由
 * {@code NoticeImageService#cleanupOrphans} 清理。
 *
 * <p>图片是物理删除的附属数据（删公告、清孤儿都真的删行），所以不继承 {@code BaseEntity}，
 * 只有 {@code create_time}/{@code update_time} 由公共字段处理器填充。
 */
@Data
@TableName("oa_notice_image")
public class NoticeImage implements Serializable {

    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 空表示尚未绑定到任何公告；绑定/解绑由保存正文时重算。 */
    @TableField("notice_id")
    private Long noticeId;

    @TableField("uploader_id")
    private Long uploaderId;

    @TableField("object_key")
    private String objectKey;

    @TableField("content_type")
    private String contentType;

    @TableField("size")
    private Long size;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;
}
