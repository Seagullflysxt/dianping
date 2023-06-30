package com.hmdp.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public LoginInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //前置拦截，在进入controller之前进行用户校验
        //1 获取请求头里的token,前端放在了authorization域里
        String token = request.getHeader("authorization");
        //2 获取redis里token对应的用户
        if (StrUtil.isBlank(token)) {
            //4 不存在， 拦截, 返回401状态码，未授权
            response.setStatus(401);
            return false;
        }
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(RedisConstants.LOGIN_USER_KEY + token);
        //3 判断用户是否存在
        if (userMap.isEmpty()) {
            //4 不存在， 拦截, 返回401状态码，未授权
            response.setStatus(401);
            return false;
        }
        //5 把查询到的hash数据转为dto数据
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        //6，保存到ThreadLocal
        UserHolder.saveUser(userDTO);

        //通过拦截器表明用户一直活跃，刷新token有效期
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token, RedisConstants.LOGIN_USER_TTL, TimeUnit.MINUTES);
        //7 放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //校验完毕，销毁用户信息，避免内存泄露
        // 移除用户
        UserHolder.removeUser();
    }
}
