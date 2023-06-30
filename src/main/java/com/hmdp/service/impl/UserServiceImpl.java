package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;

import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1.校验手机号格式是否正确
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合的话，返回错误信息
            return Result.fail("手机号格式错误");
        }

        //3.符合，生成验证码
        String code = RandomUtil.randomNumbers(6);//6位随机数字
        //4.保存验证码到session
        session.setAttribute("code", code);
        //5.发送验证码(需要调用第三方的一些短信平台，先不做，假设发过去了）
        log.debug("发送短信验证码成功, 验证码：{}",code);

        //6.返回ok
        return Result.ok();
    }

    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        /*QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("phone", phone);
        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {

        }*/
        //1 校验手机号
        if (RegexUtils.isPhoneInvalid(phone)) {
            //2.不符合的话，返回错误信息
            return Result.fail("手机号格式错误");
        }
        //2 校验验证码
        String code = loginForm.getCode();
        String cacheCode = (String)session.getAttribute("code");
        if (cacheCode == null || !code.equals((cacheCode))) {
            //3 不一致，报错
            return Result.fail("验证码错误");
        }

        //4 一致，根据手机号查询用户
        User user = query().eq("phone", phone).one();
        //5 判断用户是否存在
        if (user == null) {
            //6 不存在，创建新用户并保存用户,并且保存用户到session
            user = createUserWithPhone(phone);
        }

        //6 保存用户信息到session,使用userdto,节省内存空间，且前端不会拿到敏感信息,
        // 使用hutool里的一个工具,自动把user里的对应属性拷贝成userdto,并创建对象
        //session.setAttribute("user", user);
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));
        //基于session,session已经自动写到cookie里，请求时会自动带上sessionid,基于id得到session，所以不需要返回登录凭证
        return Result.ok();
    }

    //创建用户并保存到数据库
    private User createUserWithPhone(String phone) {
        //1 创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName(USER_NICK_NAME_PREFIX + RandomUtil.randomString(10));

        //2 保存用户，mybatisplus
        save(user);

        return user;
    }
}
