package com.lautung.oa.monitor.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.lautung.oa.framework.config.exception.BusinessException;
import com.lautung.oa.model.monitor.SysJob;
import com.lautung.oa.model.monitor.SysJobLog;
import com.lautung.oa.monitor.mapper.SysJobLogMapper;
import com.lautung.oa.monitor.mapper.SysJobMapper;
import com.lautung.oa.vo.monitor.MonitorJobVo;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 定时任务面的读与编辑（ADR 0060，#238）：列表行含可用性与最近一次执行摘要；
 * 编辑只改 cron / 启停 / 并发 / 备注（任务行由启动登记产生，不提供新增与删除）；
 * 手动执行异步触发立即返回。执行与调度在 {@link MonitorJobScheduler}。
 */
@Service
public class MonitorJobService {

    /** 执行记录接口的默认条数与上限（非分页的「最近 N 条」，上限对齐分页契约的轻量档）。 */
    private static final int DEFAULT_LOG_LIMIT = 20;
    private static final int MAX_LOG_LIMIT = 100;

    private final SysJobMapper jobMapper;
    private final SysJobLogMapper jobLogMapper;
    private final MonitorJobScheduler jobScheduler;

    public MonitorJobService(SysJobMapper jobMapper,
                             SysJobLogMapper jobLogMapper,
                             MonitorJobScheduler jobScheduler) {
        this.jobMapper = jobMapper;
        this.jobLogMapper = jobLogMapper;
        this.jobScheduler = jobScheduler;
    }

    /** 任务列表（按 id 升序）：available + 最近一次执行摘要。 */
    public List<MonitorJobVo> list() {
        List<SysJob> rows = jobMapper.selectList(
                new LambdaQueryWrapper<SysJob>().orderByAsc(SysJob::getId));
        Map<Long, SysJobLog> lastLogs = latestLogByJobId(rows);
        return rows.stream()
                .map(row -> toVo(row, lastLogs.get(row.getId())))
                .toList();
    }

    /** 编辑 cron / 启停 / 并发 / 备注（null 不覆盖），成功后 cancel + reschedule。 */
    public void update(Long id, MonitorJobUpdateCommand command) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(201, "任务不存在");
        }
        if (command.cron() != null) {
            validateCron(command.cron());
            job.setCron(command.cron());
        }
        if (command.enabled() != null) {
            job.setEnabled(command.enabled());
        }
        if (command.concurrency() != null) {
            job.setConcurrency(command.concurrency());
        }
        if (command.remark() != null) {
            job.setRemark(command.remark());
        }
        jobMapper.updateById(job);
        jobScheduler.apply(job);
    }

    /** 手动执行：任务行必须存在且 bean 存活；异步触发，立即返回。 */
    public void run(Long id) {
        SysJob job = jobMapper.selectById(id);
        if (job == null) {
            throw new BusinessException(201, "任务不存在");
        }
        if (!jobScheduler.beanExists(job.getBeanName())) {
            throw new BusinessException(201, "任务不可用：bean 已不存在");
        }
        jobScheduler.runNow(id);
    }

    /** 最近 {@code limit} 条执行记录（新在前）；limit 缺省 20。 */
    public List<SysJobLog> logs(Long id, Integer limit) {
        if (jobMapper.selectById(id) == null) {
            throw new BusinessException(201, "任务不存在");
        }
        int size = limit == null ? DEFAULT_LOG_LIMIT : limit;
        if (size < 1 || size > MAX_LOG_LIMIT) {
            throw new BusinessException(201, "limit 不合法");
        }
        return jobLogMapper.selectList(new LambdaQueryWrapper<SysJobLog>()
                .eq(SysJobLog::getJobId, id)
                .orderByDesc(SysJobLog::getId)
                .last("LIMIT " + size));
    }

    private void validateCron(String cron) {
        try {
            org.springframework.scheduling.support.CronExpression.parse(cron);
        } catch (IllegalArgumentException e) {
            throw new BusinessException(201, "cron 表达式不合法");
        }
    }

    private Map<Long, SysJobLog> latestLogByJobId(List<SysJob> rows) {
        Map<Long, SysJobLog> latest = new LinkedHashMap<>();
        if (rows.isEmpty()) {
            return latest;
        }
        List<Long> jobIds = rows.stream().map(SysJob::getId).toList();
        List<SysJobLog> logs = jobLogMapper.selectList(new LambdaQueryWrapper<SysJobLog>()
                .in(SysJobLog::getJobId, jobIds)
                .orderByDesc(SysJobLog::getId));
        for (SysJobLog log : logs) {
            latest.putIfAbsent(log.getJobId(), log);
        }
        return latest;
    }

    private MonitorJobVo toVo(SysJob row, SysJobLog last) {
        MonitorJobVo vo = new MonitorJobVo();
        vo.setId(row.getId());
        vo.setName(row.getName());
        vo.setBeanName(row.getBeanName());
        vo.setCron(row.getCron());
        vo.setEnabled(row.getEnabled());
        vo.setConcurrency(row.getConcurrency());
        vo.setRemark(row.getRemark());
        vo.setAvailable(jobScheduler.beanExists(row.getBeanName()));
        if (last != null) {
            vo.setLastTriggerType(last.getTriggerType());
            vo.setLastStatus(last.getStatus());
            vo.setLastStartedAt(last.getStartedAt());
            vo.setLastDurationMs(last.getDurationMs());
            vo.setLastMessage(last.getMessage());
        }
        return vo;
    }

    /** 编辑命令：controller 从请求体装配，null 字段不覆盖。 */
    public record MonitorJobUpdateCommand(String cron, Integer enabled, Integer concurrency, String remark) {
    }
}
