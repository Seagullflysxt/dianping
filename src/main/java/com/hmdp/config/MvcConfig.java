package com.hmdp.config;

import com.hmdp.utils.LoginInterceptor;
import com.hmdp.utils.RefreshTokenInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        //登录拦截器
        registry.addInterceptor(new LoginInterceptor())
                .excludePathPatterns(//这些路径不需要拦截,放行
                        "/shop/**", //放行以/shop打头的所有请求
                        "/shop-type/**",
                        "/blog/hot/",
                        "/upload/**",
                        "/voucher/**",
                        "/user/code",
                        "/user/login"
                ).order(1);

        //拦截所有请求，进行token刷新，拿到有效用户，保存到threadlocal,order越小，拦截器优先级越高，也就是这个先执行
        registry.addInterceptor(new RefreshTokenInterceptor(stringRedisTemplate))
                .addPathPatterns("/**")//放行所有请求
                .order(0);

    }
}
