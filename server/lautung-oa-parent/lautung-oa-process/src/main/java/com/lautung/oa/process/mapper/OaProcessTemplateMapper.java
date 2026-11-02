package com.lautung.oa.process.mapper;

import com.lautung.oa.model.process.ProcessTemplate;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * <p>
 * 审批模板 Mapper 接口
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
public interface OaProcessTemplateMapper extends BaseMapper<ProcessTemplate> {

    @Select("SELECT * FROM oa_process_template WHERE id = #{id} AND is_deleted = 0 FOR UPDATE")
    ProcessTemplate selectByIdForUpdate(Long id);

    @Select("SELECT * FROM oa_process_template WHERE status = 1 AND is_deleted = 0 ORDER BY id FOR UPDATE")
    List<ProcessTemplate> selectPublishedForUpdate();

}
