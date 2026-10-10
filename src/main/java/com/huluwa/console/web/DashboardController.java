package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.ConfigService;
import com.huluwa.console.service.DockerService;
import com.huluwa.console.service.FrpService;
import com.huluwa.console.service.MessageService;
import com.huluwa.console.service.PushService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 首页概览：聚合各模块统计，供仪表盘卡片使用。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final MessageService messageService;
    private final FrpService frpService;
    private final PushService pushService;
    private final DockerService dockerService;
    private final ConfigService configService;

    @Value("${spring.application.name:huluwa-console}")
    private String applicationName;

    public DashboardController(MessageService messageService, FrpService frpService,
                               PushService pushService, DockerService dockerService,
                               ConfigService configService) {
        this.messageService = messageService;
        this.frpService = frpService;
        this.pushService = pushService;
        this.dockerService = dockerService;
        this.configService = configService;
    }

    @GetMapping("/overview")
    public ApiResult<Map<String, Object>> overview() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("application", applicationName);
        data.put("serverTime", LocalDateTime.now().format(TIME_FORMAT));
        data.put("initialized", configService.find("security.initialized").isPresent());
        data.put("message", messageService.summary());
        data.put("frp", frpService.runtime());
        data.put("push", pushService.summary());
        data.put("docker", dockerService.summary());
        return ApiResult.ok(data);
    }
}