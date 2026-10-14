package com.huluwa.console.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huluwa.console.common.BusinessException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Frp 管理：以 frpc 容器内的 {@code /etc/frp/frpc.ini}（宿主机 {@code /root/frp/frpc.ini}）为唯一数据源，
 * 列举每条隧道监听的本地 / 远程端口，支持修改、启停、热加载与重启 frpc 容器。
 *
 * <p>配置读写通过 docker-java 在 frpc 容器内以 base64 管道完成：先写临时文件并执行
 * {@code frpc verify} 校验语法，通过后备份旧文件再原子替换；校验失败不会污染线上配置。</p>
 */
@Service
public class FrpService {

    private static final Logger log = LoggerFactory.getLogger(FrpService.class);

    /** 允许管理的代理类型；frpc 另有 stcp / xtcp 等，这里覆盖常用形态。 */
    private static final Set<String> PROXY_TYPES = Set.of("tcp", "udp", "http", "https", "stcp", "sudp", "tcpmux");

    /** 端口存活探测并发度与最长等待：隧道再多也保证列表接口秒级返回。 */
    private static final ExecutorService PROBE_EXECUTOR = Executors.newFixedThreadPool(12,
            Thread.ofPlatform().daemon().name("frp-probe-", 0).factory());

    private static final long PROBE_WAIT_SECONDS = 8L;

    private final DockerService dockerService;
    private final ObjectMapper objectMapper;

    public FrpService(DockerService dockerService, ObjectMapper objectMapper) {
        this.dockerService = dockerService;
        this.objectMapper = objectMapper;
    }

    // ------------------------------------------------------------------
    // 查询
    // ------------------------------------------------------------------

    /** 隧道列表：以 frpc.ini 为准，叠加 frpc 上报状态与本地端口存活探测。 */
    public List<Map<String, Object>> list() {
        FrpIniFile ini = current();
        List<FrpIniFile.Section> sections = new ArrayList<>(ini.tunnels());
        Map<String, Map<String, Object>> status = statusMap(ini);
        Map<String, Boolean> alive = probeAll(sections);
        List<Map<String, Object>> rows = new ArrayList<>(sections.size());
        for (FrpIniFile.Section section : sections) {
            rows.add(toRow(section, status.get(section.name), alive.get(probeKey(section))));
        }
        return rows;
    }

    /** 单个隧道（保存后回读，保证前端拿到磁盘上的真实状态）。 */
    public Map<String, Object> detail(String name) {
        FrpIniFile ini = current();
        FrpIniFile.Section section = ini.find(name);
        if (section == null) {
            throw new BusinessException(404, "隧道不存在：" + name);
        }
        return toRow(section, statusMap(ini).get(section.name), probe(section));
    }

    /** 概览统计：首页仪表盘与页面统计卡片共用。 */
    public Map<String, Object> runtime() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("container", dockerService.frpContainer());
        data.put("configPath", dockerService.frpConfigPath());
        data.put("containerConfigPath", dockerService.frpContainerConfigPath());
        data.put("hostPath", dockerService.frpMountSource());
        data.put("total", 0);
        data.put("enabled", 0);
        data.put("disabled", 0);
        data.put("running", 0);
        data.put("error", 0);
        data.put("configAvailable", false);
        data.put("containerRunning", false);
        data.put("containerStatus", "");
        data.put("message", "");

        try {
            Map<String, Object> state = dockerService.containerState(dockerService.frpContainer());
            data.put("containerRunning", Boolean.TRUE.equals(state.get("running")));
            data.put("containerStatus", state.get("status"));
            data.put("image", state.get("image"));
        } catch (BusinessException ex) {
            data.put("containerStatus", "missing");
            data.put("message", ex.getMessage());
        }

        try {
            FrpIniFile ini = current();
            Map<String, Map<String, Object>> status = statusMap(ini);
            int total = 0;
            int enabled = 0;
            int disabled = 0;
            int running = 0;
            int error = 0;
            for (FrpIniFile.Section section : ini.tunnels()) {
                total++;
                if (section.disabled) {
                    disabled++;
                    continue;
                }
                enabled++;
                String state = statusText(status.get(section.name));
                if ("running".equalsIgnoreCase(state)) {
                    running++;
                } else if (isFailed(state)) {
                    error++;
                }
            }
            data.put("total", total);
            data.put("enabled", enabled);
            data.put("disabled", disabled);
            data.put("running", running);
            data.put("error", error);
            data.put("configAvailable", true);
        } catch (BusinessException ex) {
            if (String.valueOf(data.get("message")).isBlank()) {
                data.put("message", ex.getMessage());
            }
        }
        return data;
    }

    /** 运行时设置：管理对象是哪个容器、读写哪个配置文件。 */
    public Map<String, Object> settings() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("container", dockerService.frpContainer());
        data.put("configPath", dockerService.frpConfigPath());
        data.put("containerConfigPath", dockerService.frpContainerConfigPath());
        data.put("hostPath", dockerService.frpMountSource());
        data.put("configSharedWithFrpc", dockerService.frpConfigSharedWithFrpc());
        data.put("backupPath", dockerService.frpConfigPath() + ".bak");
        data.put("containerRunning", false);
        data.put("configAvailable", false);
        data.put("message", "");
        try {
            Map<String, Object> state = dockerService.containerState(dockerService.frpContainer());
            data.put("containerRunning", Boolean.TRUE.equals(state.get("running")));
            data.put("containerStatus", state.get("status"));
            data.put("image", state.get("image"));
        } catch (BusinessException ex) {
            data.put("containerStatus", "missing");
            data.put("message", ex.getMessage());
        }
        try {
            current();
            data.put("configAvailable", true);
        } catch (BusinessException ex) {
            if (String.valueOf(data.get("message")).isBlank()) {
                data.put("message", ex.getMessage());
            }
        }
        return data;
    }

    /** 原始配置文本（供高级编辑 / 查看）。 */
    public Map<String, Object> rawConfig() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("container", dockerService.frpContainer());
        data.put("configPath", dockerService.frpConfigPath());
        data.put("content", readConfig());
        return data;
    }

    // ------------------------------------------------------------------
    // 变更
    // ------------------------------------------------------------------

    /** 新增隧道。 */
    public Map<String, Object> create(Map<String, Object> body) {
        FrpIniFile ini = current();
        String name = requireName(body.get("name"));
        if (ini.findStrict(name) != null) {
            throw new BusinessException(400, "隧道名称已存在：" + name);
        }
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        String type = normalizeType(body.get("proxyType"), true);
        values.put("type", type);
        applyFields(values, body, type, true);
        ini.add(name, values);
        writeConfig(ini.render(), "新增隧道 " + name);
        return detail(name);
    }

    /** 修改隧道（名称可在 body.newName 中一并修改）。 */
    public Map<String, Object> update(String name, Map<String, Object> body) {
        FrpIniFile ini = current();
        if (ini.find(name) == null) {
            throw new BusinessException(404, "隧道不存在：" + name);
        }
        String renamed = optionalName(body.get("newName"));
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        if (body.containsKey("proxyType")) {
            values.put("type", normalizeType(body.get("proxyType"), false));
        }
        applyFields(values, body, values.get("type"), false);
        Boolean enabled = body.containsKey("enabled") ? boolValue(body.get("enabled"), true) : null;
        ini.update(name, renamed, values, enabled);
        writeConfig(ini.render(), "修改隧道 " + name);
        return detail(renamed == null ? name : renamed);
    }

    /** 启停隧道：整段注释 / 取消注释，不破坏 frpc 语法。 */
    public Map<String, Object> toggle(String name, boolean enabled) {
        FrpIniFile ini = current();
        ini.setEnabled(name, enabled);
        writeConfig(ini.render(), (enabled ? "启用" : "停用") + "隧道 " + name);
        return detail(name);
    }

    /** 删除隧道。 */
    public void delete(String name) {
        FrpIniFile ini = current();
        ini.remove(name);
        writeConfig(ini.render(), "删除隧道 " + name);
    }

    /** 直接覆盖原始配置（保存前同样先做语法校验）。 */
    public Map<String, Object> saveRawConfig(Map<String, Object> body) {
        String content = body == null ? null : text(body.get("content"), null);
        if (content == null) {
            throw new BusinessException(400, "配置内容不能为空");
        }
        writeConfig(content, "保存 frpc.ini");
        return rawConfig();
    }

    /** 热加载：frpc reload，无需断开现有连接。 */
    public Map<String, Object> reload() {
        return dockerService.reloadFrpc();
    }

    /** 重启 frpc 容器（等价于 docker restart frpc）。 */
    public Map<String, Object> restart() {
        String container = dockerService.frpContainer();
        dockerService.restartContainer(container);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("container", container);
        data.put("restarted", true);
        return data;
    }

    // ------------------------------------------------------------------
    // 内部：读写
    // ------------------------------------------------------------------

    private FrpIniFile current() {
        return FrpIniFile.parse(readConfig());
    }

    private String readConfig() {
        return dockerService.readFrpConfig();
    }

    /** 写入配置，随后立即热加载 frpc。 */
    private void writeConfig(String content, String action) {
        dockerService.writeFrpConfig(content, action);
        applyReload(action);
    }

    /**
     * 写入后立即热加载，让端口改动即时生效。
     *
     * <p>配置文件由宿主机挂载目录直通 frpc 容器，所以宿主机上落盘即等价于 frpc 容器的
     * {@code /etc/frp/frpc.ini} 更新。热加载失败时配置已写入且语法一定合法（写入前已 verify），
     * 因此这里明确告知用户「已保存但未生效」，而不是假装成功。</p>
     */
    private void applyReload(String action) {
        Map<String, Object> reload;
        try {
            reload = dockerService.reloadFrpc();
        } catch (RuntimeException ex) {
            throw new BusinessException(500, (action == null ? "配置" : action)
                    + "已保存，但热加载失败：" + ex.getMessage() + "；请点击「重启 frpc」应用新配置");
        }
        if (!Boolean.TRUE.equals(reload.get("success"))) {
            throw new BusinessException(500, (action == null ? "配置" : action)
                    + "已保存，但热加载失败：" + value0(reload.get("output"))
                    + "；请点击「重启 frpc」应用新配置");
        }
        log.info("{}已写入并热加载 frpc", action);
    }

    // ------------------------------------------------------------------
    // 内部：行结构 → 前端结构
    // ------------------------------------------------------------------

    private Map<String, Object> toRow(FrpIniFile.Section section, Map<String, Object> status, Boolean alive) {
        Map<String, String> keys = section.keys;
        String type = value(keys, "type", "tcp");
        String localIp = value(keys, "local_ip", "127.0.0.1");
        String localPort = value(keys, "local_port", "");
        String remotePort = value(keys, "remote_port", "");
        String customDomains = value(keys, "custom_domains", "");
        String subdomain = value(keys, "subdomain", "");

        String statusText = section.disabled ? "disabled" : statusText(status);
        boolean failed = !section.disabled && isFailed(statusText);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("name", section.name);
        row.put("proxyType", type);
        row.put("localIp", localIp);
        row.put("localPort", numberOrNull(localPort));
        row.put("remotePort", numberOrNull(remotePort));
        row.put("customDomains", customDomains);
        row.put("subdomain", subdomain);
        row.put("enabled", !section.disabled);
        row.put("encryption", "true".equalsIgnoreCase(value(keys, "use_encryption", "false")));
        row.put("compression", "true".equalsIgnoreCase(value(keys, "use_compression", "false")));
        row.put("remark", value(keys, "remark", ""));
        row.put("localAddress", localPort.isBlank() ? "" : localIp + ":" + localPort);
        if (!remotePort.isBlank()) {
            row.put("remoteAddress", remotePort);
        } else if (!customDomains.isBlank()) {
            row.put("remoteAddress", customDomains);
        } else {
            row.put("remoteAddress", subdomain);
        }
        row.put("status", statusText);
        row.put("statusText", statusLabel(statusText));
        row.put("error", status == null ? "" : value0(status.get("err")));
        row.put("probe", alive == null ? "" : (alive ? "alive" : "closed"));
        row.put("probeText", alive == null ? "未探测" : (alive ? "本地端口可达" : "本地端口不可达"));
        row.put("keys", new LinkedHashMap<>(keys));
        row.put("failed", failed);
        return row;
    }

    private static String statusText(Map<String, Object> status) {
        if (status == null) {
            return "";
        }
        return value0(status.get("status"));
    }

    private static boolean isFailed(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String value = status.toLowerCase(Locale.ROOT);
        return value.contains("error") || value.contains("fail") || "closed".equals(value);
    }

    private static String statusLabel(String status) {
        if (status == null || status.isBlank()) {
            return "未知";
        }
        return switch (status.toLowerCase(Locale.ROOT)) {
            case "running" -> "运行中";
            case "new", "waiting" -> "连接中";
            case "start_error" -> "启动失败";
            case "check_failed" -> "校验失败";
            case "closed" -> "已关闭";
            case "disabled" -> "已停用";
            case "missing" -> "容器不存在";
            default -> status;
        };
    }

    // ------------------------------------------------------------------
    // 内部：frpc 运行状态（admin API）
    // ------------------------------------------------------------------

    /** 读取全部隧道运行状态；未配置 admin 端口或容器不可用时返回空表，不影响配置展示。 */
    private Map<String, Map<String, Object>> statusMap(FrpIniFile ini) {
        Map<String, Map<String, Object>> result = new LinkedHashMap<>();
        FrpIniFile.Section common = ini.common();
        if (common == null) {
            return result;
        }
        String port = value(common.keys, "admin_port", "");
        if (port.isBlank()) {
            return result;
        }
        String user = value(common.keys, "admin_user", "admin");
        String password = value(common.keys, "admin_pwd", "admin");
        String auth = Base64.getEncoder().encodeToString(
                (user + ":" + password).getBytes(StandardCharsets.UTF_8));
        String command = dockerService.frpcWget()
                + " -qO- -T 3 --header " + shellQuote("Authorization: Basic " + auth)
                + " " + shellQuote("http://127.0.0.1:" + port + "/api/status");
        try {
            Map<String, Object> exec = dockerService.execContainer(dockerService.frpContainer(),
                    List.of("sh", "-c", command));
            Integer exitCode = exec.get("exitCode") instanceof Integer code ? code : null;
            if (exitCode == null || exitCode != 0) {
                return result;
            }
            String raw = text(exec.get("output"), "");
            if (raw.isBlank() || !raw.trim().startsWith("{")) {
                return result;
            }
            JsonNode root = objectMapper.readTree(raw);
            for (Map.Entry<String, JsonNode> group : root.properties()) {
                if (!group.getValue().isArray()) {
                    continue;
                }
                for (JsonNode item : group.getValue()) {
                    String name = item.path("name").asText("");
                    if (name.isBlank()) {
                        continue;
                    }
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("name", name);
                    row.put("type", item.path("type").asText(""));
                    row.put("status", item.path("status").asText(""));
                    row.put("err", item.path("err").asText(""));
                    row.put("localAddr", item.path("local_addr").asText(""));
                    row.put("remoteAddr", item.path("remote_addr").asText(""));
                    result.put(name, row);
                }
            }
        } catch (BusinessException ex) {
            log.debug("读取 frpc 运行状态失败：{}", ex.getMessage());
        } catch (Exception ex) {
            log.debug("解析 frpc 运行状态失败：{}", ex.getMessage());
        }
        return result;
    }


    // ------------------------------------------------------------------
    // 内部：本地端口探测
    // ------------------------------------------------------------------

    private Map<String, Boolean> probeAll(Collection<FrpIniFile.Section> sections) {
        List<FrpIniFile.Section> targets = new ArrayList<>();
        for (FrpIniFile.Section section : sections) {
            if (probeable(section)) {
                targets.add(section);
            }
        }
        Map<String, Boolean> result = new LinkedHashMap<>();
        if (targets.isEmpty()) {
            return result;
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>(targets.size());
        for (FrpIniFile.Section section : targets) {
            futures.add(CompletableFuture.runAsync(() -> {
                boolean alive = probe(section);
                synchronized (result) {
                    result.put(probeKey(section), alive);
                }
            }, PROBE_EXECUTOR));
        }
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                    .get(PROBE_WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            log.debug("本地端口探测未在 {} 秒内完成：{}", PROBE_WAIT_SECONDS, ex.getMessage());
        }
        return result;
    }

    private static boolean probeable(FrpIniFile.Section section) {
        if (section.disabled) {
            return false;
        }
        if (!"tcp".equalsIgnoreCase(value(section.keys, "type", "tcp"))) {
            return false;
        }
        return !value(section.keys, "local_port", "").isBlank();
    }

    private Boolean probe(FrpIniFile.Section section) {
        if (!probeable(section)) {
            return null;
        }
        String ip = value(section.keys, "local_ip", "127.0.0.1");
        String port = value(section.keys, "local_port", "");
        return dockerService.probePort(dockerService.frpContainer(), ip, port);
    }

    private static String probeKey(FrpIniFile.Section section) {
        return section.name + "|" + value(section.keys, "local_ip", "127.0.0.1")
                + ":" + value(section.keys, "local_port", "");
    }

    // ------------------------------------------------------------------
    // 内部：字段映射与校验
    // ------------------------------------------------------------------

    /**
     * 把请求体映射为 ini 键值。
     *
     * <p>{@code created} 为 true 时补齐必填项（新增场景），否则只写请求里出现的字段，
     * 避免「编辑本地端口」把其它未提交字段一并清空。</p>
     */
    private void applyFields(Map<String, String> values, Map<String, Object> body, String type, boolean created) {
        if (body.containsKey("localIp") || created) {
            values.put("local_ip", text(body.get("localIp"), "127.0.0.1"));
        }
        if (body.containsKey("localPort") || created) {
            values.put("local_port", String.valueOf(requirePort(body.get("localPort"), "本地端口")));
        }
        boolean domain = "http".equalsIgnoreCase(type) || "https".equalsIgnoreCase(type);
        if (domain) {
            values.put("remote_port", null);
        } else if (body.containsKey("remotePort") || created) {
            Integer remotePort = optionalPort(body.get("remotePort"));
            values.put("remote_port", remotePort == null ? null : String.valueOf(remotePort));
        }
        if (body.containsKey("customDomains") || created) {
            values.put("custom_domains", blankToNull(text(body.get("customDomains"), "")));
        }
        if (domain && (body.containsKey("subdomain") || created)) {
            values.put("subdomain", blankToNull(text(body.get("subdomain"), "")));
        }
        // 关闭时不写该键，避免每次改端口都在配置文件里留下无意义的 use_encryption = false。
        if (body.containsKey("encryption")) {
            values.put("use_encryption", boolValue(body.get("encryption"), false) ? "true" : null);
        }
        if (body.containsKey("compression")) {
            values.put("use_compression", boolValue(body.get("compression"), false) ? "true" : null);
        }
        if (body.containsKey("remark")) {
            values.put("remark", blankToNull(text(body.get("remark"), "")));
        }
    }

    private static String requireName(Object value) {
        String name = text(value, null);
        if (name == null) {
            throw new BusinessException(400, "请填写隧道名称");
        }
        if (name.length() > 64) {
            throw new BusinessException(400, "隧道名称最多 64 个字符");
        }
        if (name.contains("[") || name.contains("]") || name.contains("#")
                || name.contains("\n") || name.contains("\r")) {
            throw new BusinessException(400, "隧道名称不能包含方括号、井号或换行符");
        }
        return name;
    }

    private static String optionalName(Object value) {
        if (value == null) {
            return null;
        }
        return requireName(value);
    }

    private static String normalizeType(Object value, boolean required) {
        String type = text(value, null);
        if (type == null) {
            if (required) {
                throw new BusinessException(400, "请选择代理类型");
            }
            return "tcp";
        }
        String normalized = type.toLowerCase(Locale.ROOT);
        if (!PROXY_TYPES.contains(normalized)) {
            throw new BusinessException(400, "不支持的代理类型：" + type);
        }
        return normalized;
    }

    private static int requirePort(Object value, String label) {
        Integer port = optionalPort(value);
        if (port == null || port < 1 || port > 65535) {
            throw new BusinessException(400, label + "需为 1-65535");
        }
        return port;
    }

    private static Integer optionalPort(Object value) {
        if (value == null || String.valueOf(value).isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            throw new BusinessException(400, "端口必须为数字");
        }
    }

    private static Integer numberOrNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static boolean boolValue(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(text) || "1".equals(text);
    }

    /** 空字符串归一为 null：调用方用 null 表示「删除该键 / 不写入」。 */
    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static String value(Map<String, String> keys, String name, String defaultValue) {
        String value = keys.get(name);
        return value == null || value.isBlank() ? defaultValue : value.trim();
    }

    private static String value0(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private static String text(Object value, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    /** 单引号安全包裹，避免隧道名 / 路径里的特殊字符影响容器内命令。 */
    private static String shellQuote(String value) {
        return "'" + value.replace("'", "'" + (char) 39 + "'") + "'";
    }
}
