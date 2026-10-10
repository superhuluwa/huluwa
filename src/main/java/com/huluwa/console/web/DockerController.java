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

@RestController
@RequestMapping("/api/docker")
public class DockerController {

    private final DockerService dockerService;

    public DockerController(DockerService dockerService) {
        this.dockerService = dockerService;
    }

    @GetMapping("/environment")
    public ApiResult<Map<String, Object>> environment() {
        return ApiResult.ok(dockerService.environment());
    }

    @GetMapping("/summary")
    public ApiResult<Map<String, Object>> summary() {
        return ApiResult.ok(dockerService.summary());
    }

    @GetMapping("/containers")
    public ApiResult<List<Map<String, Object>>> containers(@RequestParam(defaultValue = "true") boolean all) {
        return ApiResult.ok(dockerService.containers(all));
    }

    @GetMapping("/images")
    public ApiResult<List<Map<String, Object>>> images() {
        return ApiResult.ok(dockerService.images());
    }

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