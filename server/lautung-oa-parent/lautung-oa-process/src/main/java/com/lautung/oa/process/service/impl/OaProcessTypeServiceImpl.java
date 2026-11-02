package com.lautung.oa.process.service.impl;

import com.lautung.oa.model.process.ProcessType;
import com.lautung.oa.process.mapper.OaProcessTypeMapper;
import com.lautung.oa.process.service.OaProcessTypeService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;

/**
 * <p>
 * 审批类型 服务实现类
 * </p>
 *
 * @author atguigu
 * @since 2023-02-14
 */
@Service
public class OaProcessTypeServiceImpl extends ServiceImpl<OaProcessTypeMapper, ProcessType> implements OaProcessTypeService {

}
