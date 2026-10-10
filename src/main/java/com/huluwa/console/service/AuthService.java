package com.huluwa.console.service;

import com.huluwa.console.common.BusinessException;
import com.huluwa.console.security.TokenService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 单用户鉴权：首次启动设置密码，后续登录 / 改密。
 */
@Service
public class AuthService {

    /** 初始化标记：sys_user 有记录即视为已初始化，该键用于额外审计 */
    public static final String CFG_INITIALIZED = "security.initialized";
    public static final String DEFAULT_USERNAME = "admin";

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final int MIN_PASSWORD_LENGTH = 6;

    private final UserRepository userRepository;
    private final ConfigService configService;
    private final TokenService tokenService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserRepository userRepository, ConfigService configService, TokenService tokenService) {
        this.userRepository = userRepository;
        this.configService = configService;
        this.tokenService = tokenService;
    }

    /** 是否已完成初始化（前端据此展示「设置密码」还是「登录」） */
    public boolean initialized() {
        return userRepository.count() > 0;
    }

    @Transactional
    public String setup(String username, String password) {
        if (initialized()) {
            throw new BusinessException(400, "系统已初始化，请直接登录");
        }
        String name = normalizeUsername(username);
        validateUsername(name);
        validatePassword(password);
        String now = LocalDateTime.now().format(TIME_FORMAT);
        userRepository.insert(name, passwordEncoder.encode(password), name, now);
        configService.put(CFG_INITIALIZED, now);
        return tokenService.issue(name);
    }

    public String login(String username, String password) {
        if (!initialized()) {
            throw new BusinessException(400, "系统尚未初始化，请先设置密码");
        }
        UserRepository.SysUser user = userRepository.findByUsername(normalizeUsername(username))
                .orElseThrow(() -> new BusinessException(401, "用户名或密码错误"));
        if (!passwordEncoder.matches(password, user.password())) {
            throw new BusinessException(401, "用户名或密码错误");
        }
        return tokenService.issue(user.username());
    }

    public UserRepository.SysUser currentUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(401, "登录状态已失效，请重新登录"));
    }

    @Transactional
    public void changePassword(String username, String oldPassword, String newPassword) {
        UserRepository.SysUser user = currentUser(username);
        if (!passwordEncoder.matches(oldPassword, user.password())) {
            throw new BusinessException(400, "原密码不正确");
        }
        validatePassword(newPassword);
        if (passwordEncoder.matches(newPassword, user.password())) {
            throw new BusinessException(400, "新密码不能与原密码相同");
        }
        userRepository.updatePassword(user.username(), passwordEncoder.encode(newPassword),
                LocalDateTime.now().format(TIME_FORMAT));
        // 改密后历史令牌全部失效，前端需重新登录
        tokenService.revokeAll();
    }

    private static String normalizeUsername(String username) {
        if (username == null || username.isBlank()) {
            return DEFAULT_USERNAME;
        }
        return username.trim();
    }

    private static void validateUsername(String username) {
        if (username.length() > 32) {
            throw new BusinessException(400, "账号长度不能超过 32 位");
        }
        if (!username.matches("[A-Za-z0-9._-]+")) {
            throw new BusinessException(400, "账号只能包含字母、数字、点、下划线和短横线");
        }
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(400, "密码长度至少 " + MIN_PASSWORD_LENGTH + " 位");
        }
    }
}