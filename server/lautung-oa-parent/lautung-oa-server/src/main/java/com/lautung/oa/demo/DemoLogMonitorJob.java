package com.lautung.oa.demo;

import com.lautung.oa.monitor.job.MonitorJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * 演示用定时任务（ADR 0060，#238）：只写一行日志。启动登记会把它种成 disabled 行，
 * 演示时在任务页手动执行一次即可看到执行记录；非 demo profile 不存在这个 bean。
 */
@Component
@Profile("demo")
public class DemoLogMonitorJob implements MonitorJob {

    private static final Logger log = LoggerFactory.getLogger(DemoLogMonitorJob.class);

    @Override
    public String defaultCron() {
        // 每 10 分钟一次的占位值；行默认停用，不会自动跑。
        return "0 0/10 * * * ?";
    }

    @Override
    public void execute() {
        log.info("演示定时任务执行：本次只写这一行日志，供任务页演示执行记录。");
    }
}
