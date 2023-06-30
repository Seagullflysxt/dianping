package com.hmdp.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;


@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    /*@Autowired
    ShopTypeMapper shopTypeMapper;*/

    @Override
    public List<ShopType> getShopTypeList() {
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        /*QueryWrapper<ShopType> queryWrapper = new QueryWrapper<>();
        queryWrapper.orderByAsc("sort");
         = shopTypeMapper.selectList(queryWrapper);*/

        return shopTypes;
    }

}
