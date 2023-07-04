package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.hmdp.utils.RedisConstants.CACHE_SHOP_KEY;
import static com.hmdp.utils.RedisConstants.CACHE_TYPELIST_KEY;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result getShopTypeList() {
        //1 从redis缓存查询商铺缓存
        //cache:typelist
        Set<String> shopTypeJsonSet = stringRedisTemplate.opsForZSet().range(CACHE_TYPELIST_KEY, 0, -1);
        //2 判断缓存是否有命中
        if(shopTypeJsonSet != null && !shopTypeJsonSet.isEmpty()) {
            //3 存在，将zset转为list返回
            List<ShopType> res = new ArrayList<>();
            for (String s : shopTypeJsonSet) {
                ShopType shopType = JSONUtil.toBean(s, ShopType.class);
                res.add(shopType);
            }
            return Result.ok(res);
        }
        //4 缓存不存在， 根据id查询数据库
        List<ShopType> shopTypeList = query().orderByAsc("sort").list();
        //5 数据库里不存在， 返回错误
        if (shopTypeList == null) {
            return Result.fail("店铺类型列表不存在");
        }
        //6 数据库里存在， 写入redis，按sort排序
        for (ShopType t : shopTypeList) {
            stringRedisTemplate.opsForZSet().add(CACHE_TYPELIST_KEY, JSONUtil.toJsonStr(t), t.getSort());
        }

        //7 返回
        return Result.ok(shopTypeList);
    }

}
