package com.huluwa.console.config;

import com.huluwa.console.security.AuthInterceptor;
import com.huluwa.console.security.BookSourceAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final BookSourceAuthInterceptor bookSourceAuthInterceptor;

    public WebConfig(AuthInterceptor authInterceptor, BookSourceAuthInterceptor bookSourceAuthInterceptor) {
        this.authInterceptor = authInterceptor;
        this.bookSourceAuthInterceptor = bookSourceAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/**")
                // 书源接口改用独立的只读令牌鉴权，见下面的拦截器。
                .excludePathPatterns("/api/sources/**")
                // 鉴权本身、初始化探测、静态资源放行
                .excludePathPatterns(
                        "/api/auth/login",
                        "/api/auth/status",
                        "/api/auth/setup",
                        "/api/public/**",
                        "/api/android/agents/**",
                        "/api/terminalbuddy/android/agents/**",
                        "/api/health",
                        "/error"
                );
        // 书源接口只接受长期只读令牌；令牌管理接口不在该路径下，仍走登录鉴权。
        registry.addInterceptor(bookSourceAuthInterceptor).addPathPatterns("/api/sources/**");
    }
}
