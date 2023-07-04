package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //1 从redis缓存查询商铺信息
        //cache:shop:{id}
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2 判断缓存是否有命中
        if(StrUtil.isNotBlank(shopJson)) {//命中真实数据，返回数据
            // "a"只有有字符串才true,  null, "",为false
        //3 存在，将json字符串转为对象返回
           Shop shop = JSONUtil.toBean(shopJson, Shop.class);
           return Result.ok(shop);
        }
       //5.2 判断命中的是否是空值
        if (shopJson != null) {//到这一定是""
            return Result.fail("店铺不存在");
        }
        //4 缓存不存在， 根据id查询数据库
        Shop shop = getById(id);

        //5 数据库里不存在， 返回错误
        if (shop == null) {
            //5.1解决缓存穿透，将空值写入redis
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return Result.fail("店铺不存在");
        }
        //6 数据库里存在， 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //7 返回
        return Result.ok(shop);
    }

    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为null");
        }
        //1 更新数据库
        updateById(shop);
        //2 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);//如果出错抛异常就要回滚，整个update方法做的事情都要被撤销
        // 定义为一个事务，要加@Transactional注解

        return Result.ok();
    }
}
