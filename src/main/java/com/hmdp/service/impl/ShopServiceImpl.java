package com.hmdp.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.CacheClient;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RedisData;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;


@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//10个线程池

    @Resource
    private CacheClient cacheClient;

    @Override
    public Result queryById(Long id) {
        // 缓存穿透
        //Shop shop = queryWithPassThrough(id);
        //lambda表达式  （传入的参数） -> 函数（参数） 返回值，id2 -> getById(id2), 可以简写成 this::getById
        //Shop shop = cacheClient.
        // queryWithPassThrough(CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);
        //用互斥锁解决缓存击穿
        //Shop shop = queryWithMutex(id);
        //用逻辑过期解决缓存击穿
        //Shop shop = queryWithLogicalExpire(id);
        Shop shop = cacheClient.
                queryWithLogicalExpire(CACHE_SHOP_KEY, id, Shop.class, this::getById, 20L, TimeUnit.SECONDS);
        if (shop == null) {
            return Result.fail("店铺不存在");
        }
        return Result.ok(shop);
    }

    //用逻辑过期解决缓存击穿
    /*private Shop queryWithLogicalExpire (Long id) {
        //1 从redis缓存查询商铺信息
        //cache:shop:{id}
        String redisDateJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2 判断缓存是否有命中
        if(StrUtil.isBlank(redisDateJson)) {
            //3 缓存未命中，返回空
            return null;
        }

        //4 缓存命中，从redisdata里取出shop和expiretime
        RedisData redisData = JSONUtil.toBean(redisDateJson, RedisData.class);
        Shop shop = JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5 判断shop是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回shop
            return shop;
        }

        //5.2 逻辑过期，缓存重建
        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断获取锁是否成功
        if (isLock) {//dbcheck缓存过期,确保获取的不是另一个线程刚重建完成后释放的锁
            RedisData newRedisData = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id), redisData.getClass());
            LocalDateTime newExpire = newRedisData.getExpireTime();
            if (newExpire.isAfter(LocalDateTime.now())) {//获取锁的时候缓存已经被被人重建好了
                //释放锁
                unlock(lockKey);
                return  JSONUtil.toBean((JSONObject) redisData.getData(), Shop.class);//直接返回缓存重建好的shop
            }
            //成功拿到锁，且没有其他线程已经缓存重建好
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {//给缓存池提交一个任务
                try {
                    saveShop2Redis(id, 20L);//重建缓存
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //6.4 失败，返回过期的shop
        return shop;
    }*/

    //用互斥锁解决缓存击穿
    private Shop queryWithMutex(Long id) {
        //1 从redis缓存查询商铺信息
        //cache:shop:{id}
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2 判断缓存是否有命中
        if(StrUtil.isNotBlank(shopJson)) {//命中真实数据，返回数据
            // "a"只有有字符串才true,  null, "",为false
            //3 存在，将json字符串转为对象返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //5.2 判断命中的是否是空值
        if (shopJson != null) {//到这一定是""
            return null;
        }
        //4 实现缓存重建
        //4.1 获取互斥锁
        Shop shop = null;
        try {
            boolean isLock = tryLock(LOCK_SHOP_KEY + id);//给这条记录加锁
            //4.2 判断是否获取互斥锁成功
            if (!isLock) {
                //4.3 获取锁失败，休眠，并重试
                Thread.sleep(50);
                return queryWithMutex(id);
            }
            //4.4 获取锁成功，根据id查询数据库
            shop = getById(id);
            Thread.sleep(200);  //模拟重建延时
            //5 数据库里不存在， 返回错误
            if (shop == null) {
                //5.1解决缓存穿透，将空值写入redis
                //
                stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

                return null;
            }
            //6 数据库里存在， 写入redis
            //缓存重建完成
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        } finally {
            //7 释放互斥锁
            unlock(LOCK_SHOP_KEY + id);//给这条记录解锁
        }
        //8 返回
        return shop;
    }
    //存储null值解决缓存穿透
   /* private Shop queryWithPassThrough (Long id) {
        //1 从redis缓存查询商铺信息
        //cache:shop:{id}
        String shopJson = stringRedisTemplate.opsForValue().get(CACHE_SHOP_KEY + id);
        //2 判断缓存是否有命中
        if(StrUtil.isNotBlank(shopJson)) {//命中真实数据，返回数据
            // "a"只有有字符串才true,  null, "",为false
            //3 存在，将json字符串转为对象返回
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //5.2 判断命中的是否是空值
        if (shopJson != null) {//到这一定是""
            return null;
        }
        //4 缓存不存在， 根据id查询数据库
        Shop shop = getById(id);

        //5 数据库里不存在， 返回错误
        if (shop == null) {
            //5.1解决缓存穿透，将空值写入redis
            //
            stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        //6 数据库里存在， 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(shop), CACHE_SHOP_TTL, TimeUnit.MINUTES);

        //7 返回
        return shop;
    }*/
    //尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//防止包装类出现空值
    }
    //尝试释放锁，就是把上面的key删掉
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }

    //逻辑过期,封装有逻辑过期时间的shop，来缓存预热
    /*public void saveShop2Redis(Long id, Long expireSeconds) throws InterruptedException {
        //1 查询店铺
        Shop shop = getById(id);
        Thread.sleep(200);//模拟重建延时
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setData(shop);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSeconds));
        //3 写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));
    }*/
    @Override
    @Transactional
    public Result update(Shop shop) {
        Long id = shop.getId();
        if (id == null) {
            return Result.fail("店铺id不能为null");
        }
        //1 先更新数据库
        updateById(shop);
        //2 删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY + id);//如果出错抛异常就要回滚，整个update方法做的事情都要被撤销
        // 定义为一个事务，要加@Transactional注解

        return Result.ok();
    }
}
