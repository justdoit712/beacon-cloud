package com.cz.cache.config;

import com.cz.cache.security.CacheAuthInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcSecurityConfig implements WebMvcConfigurer {

    private final CacheAuthInterceptor cacheAuthInterceptor;

    public WebMvcSecurityConfig(CacheAuthInterceptor cacheAuthInterceptor) {
        this.cacheAuthInterceptor = cacheAuthInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(cacheAuthInterceptor)
                .addPathPatterns("/cache/**", "/test/**");
    }
}
