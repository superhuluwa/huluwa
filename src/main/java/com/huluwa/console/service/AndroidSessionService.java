package com.huluwa.console.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** 服务端配置的安卓原生入口，与聊天会话共用列表和客户端缓存。 */
@Service
public class AndroidSessionService {

    private static final String CONFIG_KEY = "chat.android.sessions";
    private static final Set<String> SUPPORTED_KINDS = Set.of("dengta", "hongguo");

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    public AndroidSessionService(ConfigService configService, ObjectMapper objectMapper) {
        this.configService = configService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> sessions(List<Map<String, Object>> chatSessions) {
        JsonNode configured;
        try {
            configured = objectMapper.readTree(configService.get(CONFIG_KEY, "[]"));
        } catch (JsonProcessingException error) {
            throw new BusinessException(500, "安卓会话配置格式错误");
        }
        if (configured == null || !configured.isArray()) {
            throw new BusinessException(500, "安卓会话配置必须为数组");
        }
        List<Map<String, Object>> result = new ArrayList<>(chatSessions);
        Set<String> ids = new HashSet<>();
        chatSessions.forEach(row -> ids.add(String.valueOf(row.get("id"))));
        List<JsonNode> entries = new ArrayList<>();
        configured.forEach(entries::add);
        // 升级已有数据库也能出现入口；显式配置（包括 enabled=false）优先。
        if (entries.stream().noneMatch(entry -> "hongguo".equals(entry.path("kind").asText()))) {
            var hongguo = objectMapper.createObjectNode();
            hongguo.put("id", "local-hongguo"); hongguo.put("title", "红果短视频");
            hongguo.put("kind", "hongguo"); hongguo.put("preview", "搜索短剧 · 选集播放 · 自动连播");
            entries.add(hongguo);
        }
        for (JsonNode entry : entries) {
            if (!entry.isObject()) {
                throw new BusinessException(500, "安卓会话配置条目必须为对象");
            }
            if (!entry.path("enabled").asBoolean(true)) continue;
            String id = entry.path("id").asText("").trim();
            String title = entry.path("title").asText("").trim();
            String kind = entry.path("kind").asText("").trim();
            if (id.isEmpty() || title.isEmpty() || !SUPPORTED_KINDS.contains(kind) || !ids.add(id)) {
                throw new BusinessException(500, "安卓会话配置的标识、名称或类型无效");
            }
            Map<String, Object> session = new LinkedHashMap<>();
            session.put("id", id);
            session.put("title", title);
            session.put("kind", kind);
            session.put("preview", entry.path("preview").asText(""));
            session.put("unread", 0);
            session.put("last", null);
            // 原生入口没有聊天活跃时间，配置修改不应将它顶到消息会话之前。
            session.put("updatedAt", "");
            result.add(session);
        }
        return result;
    }
}
