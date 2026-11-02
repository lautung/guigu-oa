package com.lautung.oa.framework.config.mp;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;

import java.util.Date;

/**
 * 公共字段的唯一写入者（ADR 0031）：插入填 {@code create_time} 与 {@code update_time}，
 * 更新刷新 {@code update_time}。
 *
 * <p>时间取应用时钟，不取数据库 {@code CURRENT_TIMESTAMP}：同一份数据里数据库会话时区与
 * 应用时区可能不同，取库时钟会让业务时间与引擎历史、Jackson 输出分叉。
 *
 * <p>已知边界：MyBatis-Plus 只在 {@code update(entity, wrapper)} 且 entity 非空时触发更新填充，
 * 用 {@code LambdaUpdateWrapper} 且不传实体的更新（含逻辑删除）不会刷新 {@code update_time}。
 */
public class CommonFieldsMetaObjectHandler implements MetaObjectHandler {

    private static final String CREATE_TIME = "createTime";
    private static final String UPDATE_TIME = "updateTime";

    @Override
    public void insertFill(MetaObject metaObject) {
        Date now = new Date();
        strictInsertFill(metaObject, CREATE_TIME, Date.class, now);
        strictInsertFill(metaObject, UPDATE_TIME, Date.class, now);
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        strictUpdateFill(metaObject, UPDATE_TIME, Date.class, new Date());
    }
}
