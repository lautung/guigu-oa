package com.lautung.oa.audit.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.lautung.oa.framework.config.mp.CommonFieldsMetaObjectHandler;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * ADR 0031 的边界：{@code sys_oper_log} 不继承 {@code BaseEntity}，公共字段处理器仍按字段名
 * 填充 {@code create_time}——前提是实体声明了 {@code FieldFill.INSERT}。这条用例把该声明钉住，
 * 免得哪天删掉 fill 后写入时间静默变成空值。
 */
class SysOperLogCommonFieldsTest {

    @BeforeAll
    static void registerTableMetadata() {
        TableInfoHelper.initTableInfo(
                new MapperBuilderAssistant(new MybatisConfiguration(), ""), SysOperLog.class);
    }

    @Test
    void commonFieldHandlerFillsCreateTimeForTheAuditEntity() {
        SysOperLog row = new SysOperLog();
        MetaObject metaObject = SystemMetaObject.forObject(row);

        new CommonFieldsMetaObjectHandler().insertFill(metaObject);

        assertNotNull(row.getCreateTime());
    }
}
