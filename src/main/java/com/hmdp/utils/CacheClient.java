package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.hmdp.entity.Shop;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.*;

@Slf4j
@Component
public class CacheClient {

    private final StringRedisTemplate stringRedisTemplate;

    public CacheClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    //存入redis
    public void set(String key, Object value, Long time, TimeUnit unit) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(value), time, unit);
    }
    //逻辑过期时间
    public void setWithLogicalExpire(String key, Object value, Long time, TimeUnit unit) {
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(value);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
        //写入redis
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(redisData));
    }

    //存储null值解决缓存穿透
    public <R, ID> R queryWithPassThrough (
            String keyPrefix, ID id, Class<R> type, Function<ID, R> dbFallback, Long time, TimeUnit unit) {//<>泛型 返回R
        String key = keyPrefix + id;
        //1 从redis缓存查询商铺信息
        //cache:shop:{id}
        String json = stringRedisTemplate.opsForValue().get(key);
        //2 判断缓存是否有命中
        if(StrUtil.isNotBlank(json)) {//命中真实数据，返回数据
            // "a"只有有字符串才true,  null, "",为false
            //3 存在，将json字符串转为对象返回
            return JSONUtil.toBean(json, type);
        }
        //5.2 判断命中的是否是空值
        if (json != null) {//到这一定是""
            return null;
        }
        //4 缓存不存在， 根据id查询数据库,这个逻辑没办法现查，必须传给来，有参数，有返回值，叫 Function
        //R r = getById(id);
        R r = dbFallback.apply(id);
        //5 数据库里不存在
        if (r == null) {
            //5.1解决缓存穿透，将空值写入redis
            //
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.MINUTES);

            return null;
        }
        //6 数据库里存在， 写入redis
       this.set(key, r, time, unit);

        //7 返回
        return r;
    }

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);//10个线程池
    //缓存击穿with 逻辑过期
    public <R, ID> R queryWithLogicalExpire (
            String prefix, ID id, Class<R> type, Function<ID, R> dbFallback,Long time, TimeUnit unit) {
        //1 从redis缓存查询商铺信息
        String key = prefix + id;

        String redisDateJson = stringRedisTemplate.opsForValue().get(key);
        //2 判断缓存是否有命中
        if(StrUtil.isBlank(redisDateJson)) {
            //3 缓存未命中，返回空
            return null;
        }

        //4 缓存命中，从redisdata里取出shop和expiretime
        RedisData redisData = JSONUtil.toBean(redisDateJson, RedisData.class);
        R r = JSONUtil.toBean((JSONObject) redisData.getData(), type);
        LocalDateTime expireTime = redisData.getExpireTime();
        //5 判断shop是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //5.1 未过期，直接返回shop
            return r;
        }

        //5.2 逻辑过期，缓存重建
        //6.缓存重建
        //6.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        //6.2 判断获取锁是否成功
        if (isLock) {//dbcheck缓存过期,确保获取的不是另一个线程刚重建完成后释放的锁
            RedisData newRedisData = JSONUtil.toBean(stringRedisTemplate.opsForValue().get(key), redisData.getClass());
            LocalDateTime newExpire = newRedisData.getExpireTime();
            if (newExpire.isAfter(LocalDateTime.now())) {//获取锁的时候缓存已经被被人重建好了
                //释放锁
                unlock(lockKey);
                return  JSONUtil.toBean((JSONObject) redisData.getData(), type);//直接返回缓存重建好的shop
            }
            //成功拿到锁，且没有其他线程已经缓存重建好
            //6.3 成功，开启独立线程，实现缓存重建
            CACHE_REBUILD_EXECUTOR.submit(() -> {//给缓存池提交一个任务
                try {
                   //query database
                    R r1 = dbFallback.apply(id);
                    //write into redis
                    this.setWithLogicalExpire(key, r1, time, unit);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                } finally {
                    //释放锁
                    unlock(lockKey);
                }
            });
        }
        //6.4 失败，返回过期的shop
        return r;
    }

    //尝试获取锁
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);//防止包装类出现空值
    }
    //尝试释放锁，就是把上面的key删掉
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
