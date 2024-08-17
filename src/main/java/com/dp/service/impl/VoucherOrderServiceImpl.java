package com.dp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.dp.dto.Result;
import com.dp.entity.SeckillVoucher;
import com.dp.entity.VoucherOrder;
import com.dp.mapper.VoucherOrderMapper;
import com.dp.service.ISeckillVoucherService;
import com.dp.service.IVoucherOrderService;
import com.dp.utils.RedisUtils;
import com.dp.utils.RedisWorker;
import com.dp.utils.UserHolder;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {
    @Autowired
    RedisWorker redisWorker;
    @Autowired
    VoucherOrderMapper voucherOrderMapper;
    @Autowired
    ISeckillVoucherService seckillVoucherService;
    @Autowired
    RedisUtils redisUtils;

    @Override
    public Result getSeckillVoucher(Long voucherId) {
        //1.根据id获取优惠券信息
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("该优惠券不存在");
        }
        //2.判断优惠券是否在时效内
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(seckillVoucher.getEndTime()) || now.isBefore(seckillVoucher.getBeginTime())) {
            return Result.fail("优惠券不在时效内");
        }
        //3.1判断库存是否充足
        int stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("优惠券已被抢完");
        }
        Long userId = UserHolder.getUser().getId();
        String key="order:"+userId;
        Boolean lock = redisUtils.getLock(key,1200L);
        if(!lock){
            return Result.fail("当前用户正在下单");
        }
        try {
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        } finally {
            redisUtils.unLock(key);
        }
        /*如果用this会锁住其他用户(因为this单例是同一个实例,全部被锁)
        如果不加intern()方法,每一次toString都是在堆内创建的新对象
        需要使用intern()方法返回常量池中的地址*//*
        synchronized (userId.toString().intern()){
            //如果不用代理对象直接调用方法会使事务失效,因为事务是通过代理机制实现的
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.creatVoucherOrder(voucherId);
        }*/
    }

    //设计多张表的共同修改加事务
    @Transactional
    public Result creatVoucherOrder(Long voucherId) {
        //3.2判断用户是否购买限额
        Long userId = UserHolder.getUser().getId();
        if (query().eq("user_id", userId)
                .eq("voucher_id", voucherId).one() != null) {
            return Result.fail("你已达到购买限额");
        }
        //4.扣减库存
        boolean success = seckillVoucherService.update().setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0).update();
        if (success != true) {
            return Result.fail("优惠券已被抢完!");
        }
        //5.创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        voucherOrder.setId(redisWorker.nextId("voucherOrder"));
        voucherOrder.setVoucherId(voucherId);
        voucherOrder.setUserId(UserHolder.getUser().getId());
        save(voucherOrder);
        return Result.ok(voucherOrder.getId());
    }
}
