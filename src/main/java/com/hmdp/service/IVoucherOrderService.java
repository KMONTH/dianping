package com.hmdp.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;

public interface IVoucherOrderService extends IService<VoucherOrder> {
    Result getSeckillVoucher(Long voucherId);
}
