package com.atguigu.model.base;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Data
public class BaseEntity implements Serializable {

    @TableId(type = IdType.AUTO)
    private Long id;

    // 公共字段由应用填（ADR 0031）：写入时刻必须和引擎历史、Jackson 输出同源，
    // 不能取数据库会话时钟——同一个库里的 CURRENT_TIMESTAMP 可能与业务时间差一个时区。
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private Date createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    private Date updateTime;

    @TableLogic //逻辑删除
    @TableField("is_deleted")
    private Integer isDeleted;

    @TableField(exist = false)
    private Map<String,Object> param = new HashMap<>();
}
