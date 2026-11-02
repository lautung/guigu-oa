package com.lautung.oa.process.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * process 域的 Mapper 注册。附件 Mapper（{@code com.lautung.oa.process.attachment}）与
 * 流程 Mapper 同属 process 域，一并扫描。
 */
@Configuration
@MapperScan(
        basePackages = {
                "com.lautung.oa.process.mapper",
                "com.lautung.oa.process.attachment"
        },
        markerInterface = BaseMapper.class)
public class ProcessMapperConfiguration {
}
