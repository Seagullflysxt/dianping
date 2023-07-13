package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
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
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;
import static com.hmdp.utils.SystemConstants.USER_NICK_NAME_PREFIX;

@Slf4j
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //private String token;

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
        //session.setAttribute("code", code);

        //4.保存验证码到redis,业务：,要设置有效期，2min
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone, code, LOGIN_CODE_TTL, TimeUnit.MINUTES);

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
        String cacheCode = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
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

        //7 保存用户信息到redis
        //7.1 随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString(true);//不带_的uuid
        //7.2 将user转为hash存到redis
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        //beantomap时可以传一些选项,
        //忽略null值
        //对字段值的一个修改器
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create()
                        .setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldeName, fieldeVaule) -> fieldeVaule.toString()));//把userdto转为map
        //long id, string nikname ,string icon
        //7.3 存到redis stringredis 要求参数都是string，string ,会把long id转为string
        stringRedisTemplate.opsForHash().putAll(LOGIN_USER_KEY + token, userMap);
        //设置token有效期
        stringRedisTemplate.expire(LOGIN_USER_KEY + token, LOGIN_USER_TTL, TimeUnit.MINUTES);
        //8 把token 返回给客户端
        return Result.ok(token);
    }

    public Result logout() {
        UserHolder.removeUser();
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
