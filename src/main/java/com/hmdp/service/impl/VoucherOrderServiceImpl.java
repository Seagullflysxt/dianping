package com.hmdp.service.impl;

import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;


@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    RedisIdWorker redisIdWorker;

    @Override

    public Result seckillVoucher(Long voucherId) {

        //此刻的秒杀券
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher.getBeginTime().isAfter(LocalDateTime.now())
                || voucher.getEndTime().isBefore(LocalDateTime.now())) {
            return Result.fail("秒杀活动尚未开始！");
        }
        //查库存
        if (voucher.getStock() < 1) {
            return Result.fail("库存不足!");
        }
        Long userId = UserHolder.getUser().getId();

        //同一用户获取锁，再下单操作
        //一人一单
        synchronized (userId.toString().intern()) {//锁住所有userid值一样的用户
            //拿到IVoucherOrderService被spring创建的代理对象
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();//拿到当前类的代理对象
            return proxy.createVoucherOrder(voucherId, userId);//事务提交到数据库  用代理对象调带事务属性的函数
        }//锁释放，同一用户再下单就会看到之前的操作

    }

    @Transactional
    public Result createVoucherOrder(Long voucherId, Long userId) {
        //一人一单
        //Long userId = UserHolder.getUser().getId();

        //Long userId = 1010L;
        int count = query().eq("user_id", userId).eq("voucher_id", voucherId).count();
        if (count > 0) {
            return Result.fail("用户已经购买过此优惠券");
        }

        //扣减库存
        //乐观锁，更新时的库存值与之前查到的库存值是否一致->更新时刻是否还有库存,超卖问题
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")//更新语句
                .eq("voucher_id", voucherId).gt("stock", 0)//where条件
                .update();
        if (!success) {
            return Result.fail("库存不足!");
        }

        //创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        long orderId = redisIdWorker.nextId("order:");
        voucherOrder.setId(orderId);
        voucherOrder.setVoucherId(voucherId);
        //Long userId = UserHolder.getUser().getId();
        //Long userId = 1010L;
        voucherOrder.setUserId(userId);
        //订单写入数据库
        save(voucherOrder);

        return Result.ok(orderId);

    }
}
