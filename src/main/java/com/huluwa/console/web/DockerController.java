package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.service.DockerService;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Docker 管理接口：容器 / 镜像 / 网络 / 卷 / 环境信息。
 * 覆盖原 Quantum 工程 Docker 管理页面的全部能力，并保留 huluwa 的主机配置接口。
 */
@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    // ---------------- 环境与汇总 ----------------

    @GetMapping("/environment")
    public ApiResult<Map<String, Object>> environment() {
        return ApiResult.ok(dockerService.environment());
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.ok(dockerService.summary());
    }

    @GetMapping("/version")
    public ApiResult<Map<String, Object>> version() {
        return ApiResult.ok(dockerService.versionInfo());
    }

    @GetMapping("/info")
    public ApiResult<Map<String, Object>> info() {
        return ApiResult.ok(dockerService.systemInfo());
    }

    // ---------------- 容器 ----------------

    @GetMapping("/containers")
    public ApiResult<List<Map<String, Object>>> containers(@RequestParam(defaultValue = "false") boolean all) {
        return ApiResult.ok(dockerService.containers(all));
    }

    @GetMapping("/containers/{containerId}")
    public ApiResult<Map<String, Object>> inspectContainer(@PathVariable String containerId) {
        return ApiResult.ok(dockerService.inspectContainer(containerId));
    }

    @GetMapping("/containers/{containerId}/stats")
    public ApiResult<Map<String, Object>> containerStats(@PathVariable String containerId) {
        return ApiResult.ok(dockerService.containerStats(containerId));
    }

    @PostMapping("/containers/stats")
    public ApiResult<Map<String, Object>> containersStats(@RequestBody(required = false) List<String> containerIds) {
        return ApiResult.ok(dockerService.containerStatsMap(containerIds));
    }

    @PostMapping("/containers")
    public ApiResult<Map<String, Object>> createContainer(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(dockerService.createContainer(body));
    }

    @PostMapping("/containers/{containerId}/start")
    public ApiResult<Void> startContainer(@PathVariable String containerId) {
        dockerService.startContainer(containerId);
        return ApiResult.ok();
    }

    @PostMapping("/containers/{containerId}/stop")
    public ApiResult<Void> stopContainer(@PathVariable String containerId) {
        dockerService.stopContainer(containerId);
        return ApiResult.ok();
    }

    @PostMapping("/containers/{containerId}/restart")
    public ApiResult<Void> restartContainer(@PathVariable String containerId) {
        dockerService.restartContainer(containerId);
        return ApiResult.ok();
    }

    @DeleteMapping("/containers/{containerId}")
    public ApiResult<Void> removeContainer(@PathVariable String containerId,
                                           @RequestParam(defaultValue = "false") boolean force,
                                           @RequestParam(defaultValue = "false") boolean removeVolumes) {
        dockerService.removeContainer(containerId, force, removeVolumes);
        return ApiResult.ok();
    }

    @GetMapping("/containers/{containerId}/logs")
    public ApiResult<String> containerLogs(@PathVariable String containerId,
                                           @RequestParam(defaultValue = "false") boolean follow,
                                           @RequestParam(defaultValue = "true") boolean stdout,
                                           @RequestParam(defaultValue = "true") boolean stderr,
                                           @RequestParam(defaultValue = "all") String tail) {
        return ApiResult.ok(dockerService.containerLogs(containerId, follow, stdout, stderr, tail));
    }

    @PostMapping("/containers/{containerId}/execute")
    public ApiResult<Map<String, Object>> execContainer(@PathVariable String containerId,
                                                        @RequestBody(required = false) Object command) {
        return ApiResult.ok(dockerService.execContainer(containerId, command));
    }

    // ---------------- 镜像 ----------------

    @GetMapping("/images")
    public ApiResult<List<Map<String, Object>>> images(@RequestParam(defaultValue = "false") boolean all) {
        return ApiResult.ok(dockerService.images(all));
    }

    @PostMapping("/images/pull")
    public ApiResult<Void> pullImage(@RequestParam String image,
                                     @RequestParam(defaultValue = "latest") String tag) {
        dockerService.pullImage(image, tag);
        return ApiResult.ok();
    }

    @DeleteMapping("/images/{imageId}")
    public ApiResult<Void> removeImage(@PathVariable String imageId,
                                       @RequestParam(defaultValue = "false") boolean force) {
        dockerService.removeImage(imageId, force);
        return ApiResult.ok();
    }

    // ---------------- 网络 ----------------

    @GetMapping("/networks")
    public ApiResult<List<Map<String, Object>>> networks() {
        return ApiResult.ok(dockerService.networks());
    }

    @GetMapping("/networks/{networkId}")
    public ApiResult<Map<String, Object>> inspectNetwork(@PathVariable String networkId) {
        return ApiResult.ok(dockerService.inspectNetwork(networkId));
    }

    @PostMapping("/networks")
    public ApiResult<Map<String, Object>> createNetwork(@RequestParam String name,
                                                        @RequestParam(defaultValue = "bridge") String driver) {
        return ApiResult.ok(dockerService.createNetwork(name, driver));
    }

    @DeleteMapping("/networks/{networkId}")
    public ApiResult<Void> removeNetwork(@PathVariable String networkId) {
        dockerService.removeNetwork(networkId);
        return ApiResult.ok();
    }

    @PostMapping("/networks/{networkId}/connect")
    public ApiResult<Void> connectNetwork(@PathVariable String networkId, @RequestParam String containerId) {
        dockerService.connectNetwork(networkId, containerId);
        return ApiResult.ok();
    }

    @PostMapping("/networks/{networkId}/disconnect")
    public ApiResult<Void> disconnectNetwork(@PathVariable String networkId, @RequestParam String containerId) {
        dockerService.disconnectNetwork(networkId, containerId);
        return ApiResult.ok();
    }

    // ---------------- 卷 ----------------

    @GetMapping("/volumes")
    public ApiResult<Map<String, Object>> volumes() {
        return ApiResult.ok(dockerService.volumes());
    }

    @PostMapping("/volumes")
    public ApiResult<Map<String, Object>> createVolume(@RequestParam String name,
                                                       @RequestParam(defaultValue = "local") String driver) {
        return ApiResult.ok(dockerService.createVolume(name, driver));
    }

    @DeleteMapping("/volumes/{volumeName}")
    public ApiResult<Void> removeVolume(@PathVariable String volumeName) {
        dockerService.removeVolume(volumeName);
        return ApiResult.ok();
    }

    // ---------------- 主机配置（huluwa 原有能力） ----------------

    @GetMapping("/hosts")
    public ApiResult<List<Map<String, Object>>> hosts() {
        return ApiResult.ok(dockerService.hosts());
    }

    @PostMapping("/hosts")
    public ApiResult<Map<String, Object>> createHost(@RequestBody Map<String, Object> body) {
        return ApiResult.ok(dockerService.createHost(body));
    }

    @DeleteMapping("/hosts/{id}")
    public ApiResult<Void> deleteHost(@PathVariable long id) {
        dockerService.deleteHost(id);
        return ApiResult.ok();
    }
}
