package com.atguigu.process.config;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * process 域的 Mapper 注册。附件 Mapper（{@code com.atguigu.process.attachment}）与
 * 流程 Mapper 同属 process 域，一并扫描。包名字符串在 E2 包名替换前保持
 * {@code com.atguigu} 原样。
 */
@Configuration
@MapperScan(
        basePackages = {
                "com.atguigu.process.mapper",
                "com.atguigu.process.attachment"
        },
        markerInterface = BaseMapper.class)
public class ProcessMapperConfiguration {
}
