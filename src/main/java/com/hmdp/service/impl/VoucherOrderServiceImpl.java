package com.hmdp.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherMapper;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisWorker;
import com.hmdp.utils.UserHolder;
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
    //涉及两张表 添加事务
    @Transactional
    @Override
    public Result   getSeckillVoucher(Long voucherId) {
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
        Long userId = UserHolder.getUser().getId();
        if(query().eq("user_id",userId).one()!=null){
            return Result.fail("你已达到购买限额");
        }
        //3.判断库存是否充足
        int stock = seckillVoucher.getStock();
        if (stock <= 0) {
            return Result.fail("优惠券已被抢完");
        }
        //4.扣减库存
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId).
                gt("stock",0).update();
        if(success!=true){
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
