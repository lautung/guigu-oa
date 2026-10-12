package com.atguigu.process.attachment;

import com.atguigu.model.process.Process;
import com.atguigu.model.system.SysUser;
import org.flowable.engine.HistoryService;
import org.flowable.engine.TaskService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class ProcessMemberAuthorizer {

    private final HistoryService historyService;
    private final TaskService taskService;
    private final List<ProcessMembershipExtension> membershipExtensions;

    public ProcessMemberAuthorizer(
            HistoryService historyService,
            TaskService taskService,
            List<ProcessMembershipExtension> membershipExtensions) {
        this.historyService = historyService;
        this.taskService = taskService;
        this.membershipExtensions = membershipExtensions;
    }

    public boolean canRead(SysUser user, Process process) {
        if (user == null || process == null || user.getId() == null) {
            return false;
        }
        if (user.getId().equals(process.getUserId()) || isApprover(user, process)) {
            return true;
        }
        return membershipExtensions.stream().anyMatch(extension -> extension.isMember(user, process));
    }

    public boolean canUpload(SysUser user, Process process) {
        if (user == null || process == null || user.getId() == null) {
            return false;
        }
        // Read-only members (for example 抄送 recipients) can download but cannot add files.
        return user.getId().equals(process.getUserId()) || isCurrentApprover(user, process);
    }

    private boolean isApprover(SysUser user, Process process) {
        return isCurrentApprover(user, process) || isHistoricApprover(user, process);
    }

    private boolean isCurrentApprover(SysUser user, Process process) {
        if (!hasProcessInstance(user, process)) {
            return false;
        }

        return taskService.createTaskQuery()
                .processInstanceId(process.getProcessInstanceId())
                .taskAssignee(user.getUsername())
                .count() > 0;
    }

    private boolean isHistoricApprover(SysUser user, Process process) {
        if (!hasProcessInstance(user, process)) {
            return false;
        }

        return historyService.createHistoricTaskInstanceQuery()
                .processInstanceId(process.getProcessInstanceId())
                .taskAssignee(user.getUsername())
                .count() > 0;
    }

    private boolean hasProcessInstance(SysUser user, Process process) {
        return process.getId() != null
                && StringUtils.hasText(user.getUsername())
                && StringUtils.hasText(process.getProcessInstanceId());
    }
}
