package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.FrpService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Frp 管理接口：直接读写 frpc 容器的 frpc.ini（宿主机 /root/frp/frpc.ini）。
 *
 * <p>路径里的 {name} 即 ini 段名，含中文时由 Spring 自动做 URL 解码。</p>
 */
@RestController
@RequestMapping("/api/frp")
public class FrpController {

    private final FrpService frpService;

    public FrpController(FrpService frpService) {
        this.frpService = frpService;
    }

    /** 隧道列表：本地 / 远程端口、运行状态与本地存活探测。 */
    @GetMapping("/tunnels")
    public ApiResult<List<Map<String, Object>>> tunnels() {
        return ApiResult.ok(frpService.list());
    }

    /** 单条隧道详情。 */
    @GetMapping("/tunnels/{name}")
    public ApiResult<Map<String, Object>> tunnel(@PathVariable String name) {
        return ApiResult.ok(frpService.detail(name));
    }

    /** 新增隧道。 */
    @PostMapping("/tunnels")
    public ApiResult<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(frpService.create(body));
    }

    /** 修改隧道（端口 / 地址 / 类型 / 名称 / 启停）。 */
    @PutMapping("/tunnels/{name}")
    public ApiResult<Map<String, Object>> update(@PathVariable String name, @RequestBody Map<String, Object> body) {
        return ApiResult.ok(frpService.update(name, body));
    }

    /** 启用 / 停用隧道（整段注释，不破坏配置文件）。 */
    @PostMapping("/tunnels/{name}/toggle")
    public ApiResult<Map<String, Object>> toggle(@PathVariable String name, @RequestParam boolean enabled) {
        return ApiResult.ok(frpService.toggle(name, enabled));
    }

    /** 删除隧道。 */
    @DeleteMapping("/tunnels/{name}")
    public ApiResult<Void> delete(@PathVariable String name) {
        frpService.delete(name);
        return ApiResult.ok();
    }

    /** 热加载配置（frpc reload，不断开现有连接）。 */
    @PostMapping("/reload")
    public ApiResult<Map<String, Object>> reload() {
        return ApiResult.ok(frpService.reload());
    }

    /** 重启 frpc 容器（docker restart frpc）。 */
    @PostMapping("/restart")
    public ApiResult<Map<String, Object>> restart() {
        return ApiResult.ok(frpService.restart());
    }

    /** 概览统计，兼容首页仪表盘。 */
    @GetMapping("/runtime")
    public ApiResult<Map<String, Object>> runtime() {
        return ApiResult.ok(frpService.runtime());
    }

    /** 运行时设置：容器名 / 配置文件路径 / 可用性。 */
    @GetMapping("/settings")
    public ApiResult<Map<String, Object>> settings() {
        return ApiResult.ok(frpService.settings());
    }

    /** 原始配置文本。 */
    @GetMapping("/config")
    public ApiResult<Map<String, Object>> config() {
        return ApiResult.ok(frpService.rawConfig());
    }

    /** 覆盖保存原始配置（保存前仍会做语法校验）。 */
    @PutMapping("/config")
    public ApiResult<Map<String, Object>> saveConfig(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(frpService.saveRawConfig(body));
    }
}
