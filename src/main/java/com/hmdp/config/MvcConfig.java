package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //配置拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(//这些路径不需要拦截,放行
                        "/shop/**", //放行以/shop打头的所有请求
                        "/shop-type/**",
                        "/blog/hot/",
                        "/upload/**",
                        "/voucher/**",
                        "/user/code",
                        "/user/login"
                );
    }
}
