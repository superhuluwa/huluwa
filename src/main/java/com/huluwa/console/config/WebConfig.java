package com.huluwa.console.config;

import com.huluwa.console.security.AuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    public WebConfig(AuthInterceptor authInterceptor) {
        this.authInterceptor = authInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 鉴权本身、初始化探测、静态资源放行
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/status",
                        "/api/auth/setup",
                        "/api/public/**",
                        "/api/android/agents/**",
                        "/api/health",
                        "/error"
                );
    }
}
