package com.lautung.oa.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.model.monitor.SysJob;
import com.lautung.oa.model.monitor.SysJobLog;
import com.lautung.oa.monitor.job.MonitorJob;
import com.lautung.oa.monitor.mapper.SysJobLogMapper;
import com.lautung.oa.monitor.mapper.SysJobMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 定时任务的登记与调度（ADR 0060，#238）：启动时把全部 {@link MonitorJob} bean 登记进
 * {@code sys_job}（缺行插入、有行不覆盖、软删行补回），再装载 {@code enabled=1} 且 bean
 * 存在的行；编辑后 cancel + reschedule。单机语义，不处理集群。
 *
 * <p>线程池是本组件私有的 {@link ThreadPoolTaskScheduler}（核心 2）：刻意不注册为
 * {@link TaskScheduler} bean，附件清理与公告图片清理的既有 {@code @Scheduled} 仍走
 * Spring Boot 的默认调度设施，互不占用线程（ADR 0060：既有 @Scheduled 不动）。
 * 登记先于 demo 播种（{@code @Order} 取 {@code Ordered.HIGHEST_PRECEDENCE}），演示种子才能改名任务行。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MonitorJobScheduler implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MonitorJobScheduler.class);

    private static final int SCHEDULER_POOL_SIZE = 2;

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final ApplicationContext applicationContext;

    private final ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();

    /** jobId → 当前 cron 的未来，编辑启停/改 cron 时 cancel + reschedule。 */
    private final ConcurrentHashMap<Long, ScheduledFuture<?>> futures = new ConcurrentHashMap<>();

    /** jobId → 上一轮是否在跑（concurrency=0 时判忙）。 */
    private final ConcurrentHashMap<Long, AtomicBoolean> running = new ConcurrentHashMap<>();

    public MonitorJobScheduler(SysJobMapper jobMapper,
                               SysJobLogMapper jobLogMapper,
                               ApplicationContext applicationContext) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.applicationContext = applicationContext;
        taskScheduler.setPoolSize(SCHEDULER_POOL_SIZE);
        taskScheduler.setThreadNamePrefix("monitor-job-");
        taskScheduler.setRemoveOnCancelPolicy(true);
        taskScheduler.afterPropertiesSet();
    }

    @Override
    public void run(ApplicationArguments args) {
        registerBeanRows();
        scheduleEnabledRows();
    }

    /**
     * 扫描全部 {@link MonitorJob} bean 登记任务行：缺行插入（enabled=0、cron 取
     * {@code defaultCron()}），已有行不覆盖用户改过的 cron；软删行补回（只翻存活标记）。
     * 幂等，可重复执行（重启等价）。
     */
    public void registerBeanRows() {
        Map<String, MonitorJob> beans = applicationContext.getBeansOfType(MonitorJob.class);
        for (Map.Entry<String, MonitorJob> entry : beans.entrySet()) {
            String beanName = entry.getKey();
            SysJob existing = jobMapper.selectAnyByBeanName(beanName);
            if (existing == null) {
                SysJob row = new SysJob();
                row.setName(beanName);
                row.setBeanName(beanName);
                row.setCron(entry.getValue().defaultCron());
                row.setEnabled(SysJob.ENABLED_OFF);
                row.setConcurrency(SysJob.CONCURRENCY_FORBID);
                jobMapper.insert(row);
                log.info("定时任务登记新行 bean={} cron={}", beanName, row.getCron());
            } else if (existing.getIsDeleted() != null && existing.getIsDeleted() != 0) {
                jobMapper.resurrect(existing.getId());
                log.info("定时任务补回软删行 bean={} id={}", beanName, existing.getId());
            }
        }
    }

    /** 启动装载：enabled=1 且 bean 存在的行进调度；bean 缺失的行不调度（读侧标「异常」）。 */
    public void scheduleEnabledRows() {
        for (SysJob job : jobMapper.selectList(
                new LambdaQueryWrapper<SysJob>().eq(SysJob::getEnabled, SysJob.ENABLED_ON))) {
            if (beanExists(job.getBeanName())) {
                apply(job);
            } else {
                log.warn("定时任务 bean 缺失，不调度 bean={} id={}", job.getBeanName(), job.getId());
            }
        }
    }

    /**
     * 编辑后的重排：先 cancel 该行现存的未来，再按现值决定是否重排
     * （enabled=1 且 bean 存在才排）。cron 非法（绕过页面直改库）时 WARN 并不排。
     */
    public void apply(SysJob job) {
        ScheduledFuture<?> previous = futures.remove(job.getId());
        if (previous != null) {
            previous.cancel(false);
        }
        if (job.getEnabled() == null || job.getEnabled() != SysJob.ENABLED_ON || !beanExists(job.getBeanName())) {
            return;
        }
        try {
            CronTrigger trigger = new CronTrigger(job.getCron());
            futures.put(job.getId(), taskScheduler.schedule(
                    () -> execute(job.getId(), SysJobLog.TRIGGER_AUTO), trigger));
        } catch (IllegalArgumentException e) {
            log.warn("定时任务 cron 非法，不调度 bean={} cron={}（{}）", job.getBeanName(), job.getCron(), e.getMessage());
        }
    }

    /** 手动执行：异步触发，立即返回；与自动调度共用 {@link #execute} 一条执行路径。 */
    public void runNow(Long jobId) {
        taskScheduler.execute(() -> execute(jobId, SysJobLog.TRIGGER_MANUAL));
    }

    /** 诊断读点：该任务当前是否挂着 cron 未来（bean 缺失/停用的行应为 false）。 */
    public boolean isScheduled(Long jobId) {
        return futures.containsKey(jobId);
    }

    /** 读侧判存活：表里的 bean 名是否还有对应的 {@link MonitorJob} bean。 */
    public boolean beanExists(String beanName) {
        return applicationContext.getBeansOfType(MonitorJob.class).containsKey(beanName);
    }

    /**
     * 自动与手动共用的执行路径：并发禁止时判忙，忙记「跳过」；异常捕获记「失败」与
     * 截断到 1000 字符的错误，不外抛；写入后执行保留策略（每任务 20 条、全局 500 条）。
     */
    void execute(Long jobId, int triggerType) {
        SysJob job = jobMapper.selectById(jobId);
        if (job == null || !beanExists(job.getBeanName())) {
            return;
        }
        MonitorJob bean = applicationContext.getBeansOfType(MonitorJob.class).get(job.getBeanName());
        AtomicBoolean busy = running.computeIfAbsent(jobId, key -> new AtomicBoolean());
        if (job.getConcurrency() != null && job.getConcurrency() == SysJob.CONCURRENCY_FORBID
                && !busy.compareAndSet(false, true)) {
            writeLog(job, triggerType, SysJobLog.STATUS_SKIPPED, "上一轮尚未结束，本次跳过",
                    System.currentTimeMillis(), System.currentTimeMillis());
            trim(jobId);
            return;
        }
        long startedAt = System.currentTimeMillis();
        try {
            bean.execute();
            writeLog(job, triggerType, SysJobLog.STATUS_SUCCESS, null, startedAt, System.currentTimeMillis());
        } catch (Exception e) {
            log.warn("定时任务执行失败 bean={} job={}", job.getBeanName(), job.getName(), e);
            writeLog(job, triggerType, SysJobLog.STATUS_FAILURE, SysJobLog.truncateMessage(String.valueOf(e)),
                    startedAt, System.currentTimeMillis());
        } finally {
            busy.set(false);
        }
        trim(jobId);
    }

    private void writeLog(SysJob job, int triggerType, int status, String message,
                          long startedAtMillis, long finishedAtMillis) {
        SysJobLog row = new SysJobLog();
        row.setJobId(job.getId());
        row.setJobName(job.getName());
        row.setTriggerType(triggerType);
        row.setStatus(status);
        row.setMessage(message);
        row.setStartedAt(new Date(startedAtMillis));
        row.setFinishedAt(new Date(finishedAtMillis));
        row.setDurationMs(finishedAtMillis - startedAtMillis);
        jobLogMapper.insert(row);
        if (status == SysJobLog.STATUS_FAILURE || status == SysJobLog.STATUS_SKIPPED) {
            log.warn("定时任务记录 bean={} status={} message={}", job.getBeanName(), status, message);
        }
    }

    /** 保留策略：写入路径就地清（每任务最近 20 条、全局最近 500 条），不依赖外部定时器。 */
    private void trim(Long jobId) {
        jobLogMapper.trimPerJob(jobId, SysJobLog.RETENTION_PER_JOB);
        jobLogMapper.trimGlobal(SysJobLog.RETENTION_GLOBAL);
    }
}
