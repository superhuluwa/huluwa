package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Serialize both apps on the shared Debian host, including updates. */
@Service
public class AndroidBuildCoordinator {
    private final List<AndroidBuildService> projects;

    public AndroidBuildCoordinator(AndroidBuildService huluwa, TerminalBuddyBuildService terminalBuddy) {
        projects = List.of(huluwa, terminalBuddy.builds());
    }

    public synchronized Map<String, Object> submit(AndroidBuildService project, String type, String username, String serverId) {
        for (var candidate : projects) {
            if (candidate.list(100).stream().anyMatch(task -> Set.of("QUEUED", "RUNNING", "CANCELLING").contains(task.get("status"))))
                throw new BusinessException(409, "已有应用构建任务执行中，请等待完成或取消后再操作");
        }
        return project.submit(type, username, serverId);
    }
}
