package com.hmdp.utils;

import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

public class LoginInterceptor implements HandlerInterceptor {
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //前置拦截，在进入controller之前进行用户校验
        //1 从request里取到session
        HttpSession session = request.getSession();
        //2 获取session里的用户
        UserDTO user = (UserDTO)session.getAttribute("user");
        //3 判断用户是否存在
        if (user == null) {
            //4 不存在， 拦截, 返回401状态码，未授权
            response.setStatus(401);
            return false;
        }

        //5 存在，保存到ThreadLocal
        UserHolder.saveUser(user);
        //6 放行
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //校验完毕，销毁用户信息，避免内存泄露
        // 移除用户
        UserHolder.removeUser();
    }
}
