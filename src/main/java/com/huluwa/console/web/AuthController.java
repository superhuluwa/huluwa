package com.huluwa.console.web;

import com.huluwa.console.common.ApiResult;
import com.huluwa.console.common.BusinessException;
import com.huluwa.console.security.AuthInterceptor;
import com.huluwa.console.service.AuthService;
import com.huluwa.console.service.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 无需登录：探测系统是否已初始化，供前端决定展示设置密码还是登录 */
    @GetMapping("/status")
    public ApiResult<Map<String, Object>> status() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("initialized", authService.initialized());
        data.put("username", AuthService.DEFAULT_USERNAME);
        return ApiResult.ok(data);
    }

    /** 无需登录：首次启动设置密码，成功后直接返回令牌 */
    @PostMapping("/setup")
    public ApiResult<Map<String, Object>> setup(@Valid @RequestBody SetupRequest request) {
        String token = authService.setup(request.username(), request.password());
        return ApiResult.ok(tokenPayload(token, request.username()));
    }

    /** 无需登录：登录 */
    @PostMapping("/login")
    public ApiResult<Map<String, Object>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.username(), request.password());
        return ApiResult.ok(tokenPayload(token, request.username()));
    }

    /** 需要登录：当前用户信息 */
    @GetMapping("/me")
    public ApiResult<Map<String, Object>> me(HttpServletRequest request) {
        UserRepository.SysUser user = authService.currentUser(username(request));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("username", user.username());
        data.put("nickname", user.nickname() == null ? user.username() : user.nickname());
        data.put("createdAt", user.createdAt());
        return ApiResult.ok(data);
    }

    /** 需要登录：修改密码，成功后历史令牌失效需重新登录 */
    @PostMapping("/password")
    public ApiResult<Void> changePassword(@Valid @RequestBody ChangePasswordRequest body,
                                          HttpServletRequest request) {
        authService.changePassword(username(request), body.oldPassword(), body.newPassword());
        return ApiResult.ok();
    }

    private static String username(HttpServletRequest request) {
        Object value = request.getAttribute(AuthInterceptor.ATTR_USERNAME);
        if (value == null) {
            throw new BusinessException(401, "登录状态已失效，请重新登录");
        }
        return value.toString();
    }

    private static Map<String, Object> tokenPayload(String token, String username) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("token", token);
        data.put("tokenType", "Bearer");
        data.put("username", username);
        return data;
    }

    public record SetupRequest(
            @NotBlank(message = "请输入管理员账号")
            @Size(max = 32, message = "账号长度不能超过 32 位")
            String username,
            @NotBlank(message = "请输入密码")
            @Size(min = 6, max = 64, message = "密码长度需为 6-64 位")
            String password) {
    }

    public record LoginRequest(
            String username,
            @NotBlank(message = "请输入密码") String password) {
    }

    public record ChangePasswordRequest(
            @NotBlank(message = "请输入原密码") String oldPassword,
            @NotBlank(message = "请输入新密码")
            @Size(min = 6, max = 64, message = "新密码长度需为 6-64 位")
            String newPassword) {
    }
}