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

import javax.annotation.Resource;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        //1 从redis缓存查询商铺缓存
        //cache:shop:{id}
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2 判断缓存是否有命中
        if(StrUtil.isNotBlank(shopJson)) {
        //3 存在，将json字符串转为对象返回
           Shop shop = JSONUtil.toBean(shopJson, Shop.class);
           return Result.ok(shop);
        }
       
        //4 缓存不存在， 根据id查询数据库
        Shop shop = getById(id);

        //5 数据库里不存在， 返回错误
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        //6 数据库里存在， 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop));

        //7 返回
        return Result.ok(shop);
    }
}
