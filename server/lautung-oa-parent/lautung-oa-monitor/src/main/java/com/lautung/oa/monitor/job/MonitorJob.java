package com.lautung.oa.monitor.job;

/**
 * 定时任务的注册接口（ADR 0060，#238）：实现本接口的 Spring bean 在启动时把 bean 名
 * 登记进 {@code sys_job}（缺行插入、有行不覆盖），调度由表驱动。
 *
 * <p>不是任意代码调用串（GLOSSARY「定时任务」）：cron 只能在页面上改实现声明的默认值，
 * 执行体只能是这个 bean 的 {@link #execute()}。
 */
public interface MonitorJob {

    /** 登记缺行时写入的默认 cron（Spring 6 字段 cron：秒 分 时 日 月 周）。 */
    String defaultCron();

    /** 任务体：异常由执行路径捕获并记「失败」，不外抛。 */
    void execute();
}
